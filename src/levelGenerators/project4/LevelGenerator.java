package levelGenerators.project4;

import engine.core.MarioLevelGenerator;
import engine.core.MarioLevelModel;
import engine.core.MarioTimer;

import java.util.List;
import java.util.Random;

public class LevelGenerator implements MarioLevelGenerator {
    public static class Report {
        public long seed;
        public double tau;
        public LevelEvaluator.Shape shape;
        public LevelEvaluator.Metrics metrics;
        public int repairs;
        public boolean agentVerified;
        public AgentRepair.Playthrough playthrough;
        public double initialBestFitness, finalBestFitness;
        public LevelEvaluator.Metrics initialMetrics;
        public List<Double> bestHistory, meanHistory;
        public Genome genome;
        public LevelBuilder.Result build;
    }

    private long seed;
    private double tau;
    private LevelEvaluator.Shape shape = null;     // null => derived from the seed
    public boolean useAgent = true;
    public String verifier = AgentRepair.DEFAULT_AGENT;
    public int maxRepairs = 8;
    public int populationSize = 48, generations = 60;
    public Report lastReport;

    public LevelGenerator() {
        this(System.nanoTime());
    }

    public LevelGenerator(long seed) {
        this.seed = seed;
    }

    public LevelGenerator(long seed, double tau, LevelEvaluator.Shape shape) {
        this.seed = seed;
        this.tau = tau;
        this.shape = shape;
    }

    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public String getGeneratedLevel(MarioLevelModel model, MarioTimer timer) {
        Random master = new Random(mix(seed));
        double t = tau >= 0 ? tau : 0.3 + 0.55 * master.nextDouble();
        LevelEvaluator.Shape sh = shape != null ? shape : LevelEvaluator.Shape.values()[master.nextInt(3)];
        int W = model.getWidth(), H = model.getHeight();

        //step 1 and 2 grammar + genetic search
        Evolver ev = new Evolver(W, H, sh, t, new Random(master.nextLong()));
        ev.populationSize = populationSize;
        ev.generations = generations;
        List<Evolver.Individual> pop = ev.run(timer);
        Evolver.Individual best = pop.get(0);

        Report rep = new Report();
        rep.seed = seed;
        rep.tau = t;
        rep.shape = sh;
        rep.initialBestFitness = ev.bestHistory.get(0);
        rep.initialMetrics = ev.initialBestMetrics;
        rep.bestHistory = ev.bestHistory;
        rep.meanHistory = ev.meanHistory;

        //step 3 agent verification and targeted repair
        if (useAgent && timer.getRemainingTime() > 10000) {
            Genome g = best.genome.copy();
            for (int attempt = 0; attempt <= maxRepairs; attempt++) {
                LevelBuilder.Result br = LevelBuilder.build(g, W, H);
                String level = br.model.getMap();
                AgentRepair.Playthrough p = AgentRepair.playConfirmed(level, verifier);
                rep.playthrough = p;
                best = ev.reevaluate(g.copy());
                if (p.won) {
                    rep.agentVerified = true;
                    break;
                }
                if (attempt == maxRepairs || timer.getRemainingTime() < 10000) break;
                int xFail = (int) (p.completion * (W - 3));
                if (!AgentRepair.repair(g, br, xFail)) break;
                rep.repairs++;
            }
        }
        rep.finalBestFitness = best.metrics.fitness;
        rep.metrics = best.metrics;
        rep.genome = best.genome;
        rep.build = best.build;
        lastReport = rep;
        return best.build.model.getMap();
    }

    @Override
    public String getGeneratorName() {
        return "HybridGrammarGAAgentRepair";
    }
}
