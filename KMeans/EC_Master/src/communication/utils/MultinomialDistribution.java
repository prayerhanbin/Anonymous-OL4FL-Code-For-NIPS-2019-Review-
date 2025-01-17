package communication.utils;

import java.util.Random;

public class MultinomialDistribution {
    private static Random generator = new Random();
    private static double[] distribution;
    private static int range;

    public static int sampleFromMultinomialDistribution(double[] probabilities){
        createMultinomialDistribution(probabilities);
        return sample();
    }

    public static void createMultinomialDistribution(double[] probabilities){
        range = probabilities.length;
        distribution = new double[range];
        double sumProb = 0;
        for (double value : probabilities){
            sumProb += value;
        }
        double position = 0;
        for (int i = 0; i < range; ++i){
            position += probabilities[i] / sumProb;
            distribution[i] = position;
        }
        distribution[range -1] = 1.0;
    }

    public static int sample() {
        double uniform = generator.nextDouble();
        for (int i = 0; i < range; ++i){
            if (uniform < distribution[i]){
                return i;
            }
        }
        return range - 1;
    }
}
