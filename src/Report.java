import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates batch measurements by independent JVM fork and writes portable SVG plots. */
public final class Report {
    private record Key(String workload, String structure, int n) {}
    private record Summary(Key key, int samples, int forks, double median, double min, double max) {}
    private static final String[] COLORS = {"#1463a3", "#c04e20", "#437c43", "#7952a1"};

    public static void main(String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("Usage: Report raw-1.csv [raw-2.csv ...]");
        Locale.setDefault(Locale.ROOT);
        Map<Key, Map<Integer, List<Double>>> groups = new LinkedHashMap<>();
        for (String file : args) {
            List<String> lines = Files.readAllLines(Path.of(file));
            for (int i = 1; i < lines.size(); i++) {
                String[] fields = lines.get(i).split(",");
                Key key = new Key(fields[3], fields[2], Integer.parseInt(fields[4]));
                groups.computeIfAbsent(key, unused -> new TreeMap<>())
                        .computeIfAbsent(Integer.parseInt(fields[0]), unused -> new ArrayList<>())
                        .add(Double.parseDouble(fields[8]));
            }
        }
        List<Summary> summaries = new ArrayList<>();
        for (var group : groups.entrySet()) {
            List<Double> forkMedians = new ArrayList<>();
            int samples = 0;
            for (List<Double> samplesInFork : group.getValue().values()) {
                forkMedians.add(median(samplesInFork));
                samples += samplesInFork.size();
            }
            forkMedians.sort(Double::compare);
            summaries.add(new Summary(group.getKey(), samples, forkMedians.size(), median(forkMedians),
                    forkMedians.get(0), forkMedians.get(forkMedians.size() - 1)));
        }
        summaries.sort(Comparator.comparing((Summary s) -> s.key().workload())
                .thenComparing(s -> s.key().structure()).thenComparingInt(s -> s.key().n()));
        Path tables = Path.of("results/tables");
        Path plots = Path.of("results/plots");
        Files.createDirectories(tables);
        Files.createDirectories(plots);
        StringBuilder csv = new StringBuilder("workload,structure,n,samples,forks,median_ns_per_op,min_fork_median_ns,max_fork_median_ns\n");
        StringBuilder md = new StringBuilder("# Measured benchmark results\n\n"
                + "Each estimate is the median of the per-JVM medians. Ranges span the smallest and largest fork medians; "
                + "they are not confidence intervals. Units: nanoseconds per API operation (ns/op). "
                + "Pair workloads divide by both calls. See the root README for setup and limitations.\n\n");
        Map<String, List<Summary>> workloads = new TreeMap<>();
        for (Summary s : summaries) {
            csv.append(String.format("%s,%s,%d,%d,%d,%.3f,%.3f,%.3f%n", s.key().workload(), s.key().structure(),
                    s.key().n(), s.samples(), s.forks(), s.median(), s.min(), s.max()));
            workloads.computeIfAbsent(s.key().workload(), unused -> new ArrayList<>()).add(s);
        }
        for (var workload : workloads.entrySet()) {
            md.append("## ").append(workload.getKey()).append("\n\n")
                    .append("| Structure | n | Median ns/op | Fork median range | Samples / forks |\n")
                    .append("|---|---:|---:|---:|---:|\n");
            for (Summary s : workload.getValue()) {
                md.append(String.format("| %s | %,d | %.2f | %.2f–%.2f | %d / %d |%n",
                        s.key().structure(), s.key().n(), s.median(), s.min(), s.max(), s.samples(), s.forks()));
            }
            md.append("\n![").append(workload.getKey()).append("](../plots/")
                    .append(workload.getKey()).append(".svg)\n\n");
            Files.writeString(plots.resolve(workload.getKey() + ".svg"), plot(workload.getKey(), workload.getValue()));
        }
        Files.writeString(tables.resolve("summary.csv"), csv);
        Files.writeString(tables.resolve("summary.md"), md.toString().stripTrailing() + "\n");
        System.out.printf("Generated %d summary rows and %d SVG plots%n", summaries.size(), workloads.size());
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private static String plot(String workload, List<Summary> data) {
        double lowest = data.stream().mapToDouble(Summary::min).min().orElseThrow();
        double highest = data.stream().mapToDouble(Summary::max).max().orElseThrow();
        int lowerPower = (int) Math.floor(Math.log10(Math.max(0.001, lowest)));
        int upperPower = (int) Math.ceil(Math.log10(highest));
        if (upperPower <= lowerPower) upperPower = lowerPower + 1;
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"920\" height=\"550\" viewBox=\"0 0 920 550\" role=\"img\">\n");
        svg.append("<title>").append(workload).append(" — median nanoseconds per operation</title>\n")
                .append("<desc>Logarithmic axes; points show medians of fork medians and whiskers show the range of fork medians.</desc>\n")
                .append("<rect width=\"920\" height=\"550\" fill=\"white\"/>\n")
                .append("<g font-family=\"Arial, sans-serif\" fill=\"#222\">\n");
        label(svg, 80, 32, workload.replace('_', ' ') + " — measured time per operation", 21, "start");
        label(svg, 80, 55, "Median of fork medians; whiskers = min–max fork medians", 13, "start");
        for (int power = lowerPower; power <= upperPower; power++) {
            double y = y(Math.pow(10, power), lowerPower, upperPower);
            line(svg, 90, y, 670, y, "#ddd", 1);
            label(svg, 78, y + 5, "10^" + power, 12, "end");
        }
        for (int n : new int[] {1000, 10000, 100000}) {
            double x = x(n);
            line(svg, x, 90, x, 420, "#e5e5e5", 1);
            label(svg, x, 445, String.format("%,d", n), 13, "middle");
        }
        line(svg, 90, 420, 670, 420, "#555", 1);
        line(svg, 90, 90, 90, 420, "#555", 1);
        label(svg, 380, 478, "n — structure size / build count (log scale)", 14, "middle");
        svg.append("<text x=\"22\" y=\"260\" transform=\"rotate(-90 22 260)\" text-anchor=\"middle\" font-size=\"14\">Time (ns/op, log scale)</text>\n");
        Map<String, List<Summary>> series = new TreeMap<>();
        for (Summary s : data) series.computeIfAbsent(s.key().structure(), unused -> new ArrayList<>()).add(s);
        int colorIndex = 0;
        for (var entry : series.entrySet()) {
            String color = COLORS[colorIndex];
            int legendY = 110 + colorIndex++ * 30;
            line(svg, 700, legendY - 4, 725, legendY - 4, color, 2);
            label(svg, 733, legendY, entry.getKey(), 13, "start");
            List<Summary> points = entry.getValue();
            points.sort(Comparator.comparingInt(s -> s.key().n()));
            svg.append("<polyline fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"2\" points=\"");
            for (Summary s : points) svg.append(String.format("%.2f,%.2f ", x(s.key().n()), y(s.median(), lowerPower, upperPower)));
            svg.append("\"/>\n");
            for (Summary s : points) {
                double px = x(s.key().n());
                double lowY = y(s.min(), lowerPower, upperPower);
                double highY = y(s.max(), lowerPower, upperPower);
                line(svg, px, lowY, px, highY, color, 1);
                line(svg, px - 4, lowY, px + 4, lowY, color, 1);
                line(svg, px - 4, highY, px + 4, highY, color, 1);
                svg.append(String.format("<circle cx=\"%.2f\" cy=\"%.2f\" r=\"4\" fill=\"%s\"><title>%s: n=%d, %.2f ns/op</title></circle>%n",
                        px, y(s.median(), lowerPower, upperPower), color, entry.getKey(), s.key().n(), s.median()));
            }
        }
        label(svg, 80, 515, "Source: results/tables/raw-*.csv; run timestamps in environment-*.txt", 12, "start");
        label(svg, 80, 535, "Batch averages; tiny timings include JVM optimization effects. See README for workload definitions.", 12, "start");
        return svg.append("</g>\n</svg>\n").toString();
    }

    private static double x(int n) { return 100 + (Math.log10(n) - 3) * 280; }
    private static double y(double value, int lower, int upper) {
        return 420 - (Math.log10(Math.max(0.001, value)) - lower) / (upper - lower) * 330;
    }

    private static void line(StringBuilder svg, double x1, double y1, double x2, double y2, String color, int width) {
        svg.append(String.format("<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"%s\" stroke-width=\"%d\"/>%n",
                x1, y1, x2, y2, color, width));
    }

    private static void label(StringBuilder svg, double x, double y, String text, int size, String anchor) {
        svg.append(String.format("<text x=\"%.2f\" y=\"%.2f\" font-size=\"%d\" text-anchor=\"%s\">%s</text>%n", x, y, size, anchor, text));
    }
}
