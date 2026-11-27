package levelGenerators.project4;

import engine.core.MarioLevelModel;

import java.util.List;

public class LevelEvaluator {
    public enum Shape {RAMP, WAVE, ARC}

    public static class Metrics {
        public double fitness;
        public double pacingMAE;
        public double variety;
        public int distinctTypes;
        public int repetitions;
        public int violations;
        public double reward;
        public double fillFraction;
        public double flatFraction;
        public double meanDifficulty;
        public double maxDifficulty;
        public int pits, enemies, pipes, bumpables, coins, launchers;
        public double hazardDensity;
        public int[] typeCount = new int[SegmentType.COUNT];
    }

    public static double shape(Shape s, double t) {
        switch (s) {
            case RAMP:
                return 0.2 + 0.8 * t;
            case WAVE:
                return (0.25 + 0.75 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 2 * t - Math.PI / 2))) * (0.6 + 0.4 * t);
            case ARC:
            default:
                return 0.2 + 0.8 * Math.exp(-Math.pow((t - 0.7) / 0.28, 2));
        }

    }

    public static double target(Shape s, double tau, double t) {
        return 0.12 + 0.75 * tau * shape(s, t);
    }

    public static Metrics evaluate(LevelBuilder.Result r, Shape shape, double tau) {
        Metrics mt = new Metrics();
        MarioLevelModel m = r.model;
        int W = m.getWidth();
        int body = r.bodyEnd - r.bodyStart;

        // difficulty profile per column
        double[] prof = new double[W];
        int covered = 0;
        for (int i = 0; i < r.placed; i++) {
            for (int x = r.segStart[i]; x < r.segStart[i] + r.segLen[i]; x++) prof[x] = r.segDiff[i];
            covered += r.segLen[i];
            mt.typeCount[r.segType[i]]++;
            mt.meanDifficulty += r.segDiff[i];
            mt.maxDifficulty = Math.max(mt.maxDifficulty, r.segDiff[i]);
        }
        mt.meanDifficulty /= Math.max(1, r.placed);
        mt.fillFraction = covered / (double) body;
        int flatCols = body - covered;
        for (int i = 0; i < r.placed; i++) if (r.segType[i] == SegmentType.FLAT.ordinal()) flatCols += r.segLen[i];
        mt.flatFraction = flatCols / (double) body;

        int win = 12;
        double err = 0;
        for (int x = r.bodyStart; x < r.bodyEnd; x++) {
            double sum = 0;
            int n = 0;
            for (int k = -win; k <= win; k++) {
                int cx = x + k;
                if (cx >= r.bodyStart && cx < r.bodyEnd) {
                    sum += prof[cx];
                    n++;
                }
            }
            double t = (x - r.bodyStart) / (double) body;
            err += Math.abs(sum / n - target(shape, tau, t));
        }
        mt.pacingMAE = err / body;

        // variety and repetition
        double H = 0;
        for (int c : mt.typeCount) {
            if (c > 0) {
                mt.distinctTypes++;
                double p = c / (double) r.placed;
                H -= p * Math.log(p);
            }
        }
        mt.variety = H / Math.log(SegmentType.COUNT);
        for (int i = 1; i < r.placed; i++) {
            if (r.segType[i] == r.segType[i - 1]) mt.repetitions++;
        }

        // ---- reward / content counts
        int firstPower = Integer.MAX_VALUE;
        int pitRun = 0;
        for (int x = 0; x < W; x++) {
            boolean voidCol = m.getBlock(x, m.getHeight() - 1) == MarioLevelModel.EMPTY;
            if (voidCol) {
                if (pitRun == 0) mt.pits++;
                pitRun++;
            } else {
                pitRun = 0;
            }
            for (int y = 0; y < m.getHeight(); y++) {
                char c = m.getBlock(x, y);
                if (c == MarioLevelModel.SPECIAL_QUESTION_BLOCK || c == MarioLevelModel.SPECIAL_BRICK) {
                    firstPower = Math.min(firstPower, x);
                }
                for (char b : MarioLevelModel.getBumpableTiles()) if (b == c) mt.bumpables++;
                for (char e : MarioLevelModel.getEnemyCharacters()) if (e == c) mt.enemies++;
                if (c == MarioLevelModel.COIN) mt.coins++;
                if (c == MarioLevelModel.BULLET_BILL && (y == 0 || m.getBlock(x, y - 1) != c)) mt.launchers++;
                if ((c == MarioLevelModel.PIPE || c == MarioLevelModel.PIPE_FLOWER) && y > 0
                        && m.getBlock(x, y - 1) != c && x > 0 && m.getBlock(x - 1, y) != c) mt.pipes++;
            }
        }
        double reward = 0;
        reward += firstPower <= 45 ? 0.5 : 0;
        reward += Math.min(1.0, mt.bumpables / 8.0) * 0.3;
        reward += Math.min(1.0, mt.coins / 20.0) * 0.2;
        mt.reward = reward;

        double hazards = mt.pits + mt.enemies + mt.pipes + mt.launchers;
        double wanted = 6 + 20 * tau;
        mt.hazardDensity = hazards / wanted;

        // ---- hard constraints
        List<String> viol = LevelValidator.check(m);
        mt.violations = viol.size();

        double f = 0;
        f += 4.0 * (1.0 - Math.min(1.0, mt.pacingMAE / 0.25));
        f += 1.5 * mt.variety;
        f += 0.8 * Math.min(1.0, mt.distinctTypes / 7.0);
        f -= 0.25 * mt.repetitions;
        f += 1.0 * mt.reward;
        f -= 3.0 * (1.0 - mt.fillFraction);
        f -= 5.0 * Math.max(0, mt.flatFraction - 0.2);
        f -= 3.0 * Math.min(1.0, Math.abs(1.0 - mt.hazardDensity));
        f -= 10.0 * mt.violations;
        mt.fitness = f;
        return mt;
    }
}
