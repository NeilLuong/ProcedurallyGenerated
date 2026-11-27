package levelGenerators.project4;

public enum SegmentType {
    FLAT(0.00, false),
    REWARD(0.10, false),
    STAIRS(0.40, false),
    HILL(0.50, false),     // raised ground plateau, enemies on top at higher difficulty
    PIPES(0.60, false),    // 1-3 pipes, optionally with piranha plants
    ENEMIES(0.70, false),  // enemy group on flat ground
    BULLET(0.70, false),   // bullet bill launcher(s)
    GAP(0.80, true),       // pit with coin arc as a visual hint
    BRIDGE(1.00, true);

    public final double weight;
    public final boolean gapLike;

    SegmentType(double weight, boolean gapLike) {
        this.weight = weight;
        this.gapLike = gapLike;
    }

    public static final int COUNT = values().length;

    public double difficulty(double d) {
        if (weight == 0) return 0;
        return weight * (0.3 + 0.7 * d);
    }
}
