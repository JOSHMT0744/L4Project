/*******************************************************************************
 * Entropy-Regularized Policy
 * Selects actions using softmax over Q-values at belief
 *******************************************************************************/

package solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pomdp.POMDP;

/**
 * Policy that selects actions by sampling from softmax distribution
 * over Q-values at the current belief.
 */
public class ERPolicy {
    
    private POMDP pomdp;
    private List<List<AlphaVector>> qFunctions;  // Q-functions indexed by action
    private double lambda;  // temperature parameter
    private Random rnd;
    
    public ERPolicy(
        POMDP pomdp, 
        List<List<AlphaVector>> qFunctions, 
        double lambda, 
        Random rnd) {
            
        this.pomdp = pomdp;
        this.qFunctions = qFunctions;
        this.lambda = lambda;
        this.rnd = rnd;
    }
    
    /**
     * Create policy from ERPBVI solver
     */
    public ERPolicy(POMDP pomdp, ERPBVI solver, Random rnd) {
        this.pomdp = pomdp;
        this.qFunctions = solver.getQFunctions();
        this.lambda = solver.getLambda();
        this.rnd = rnd;
    }
    
    /**
     * Create policy from ERPerseus solver's value function
     * Extracts Q-functions by grouping alpha vectors by action
     */
    public ERPolicy(POMDP pomdp, ArrayList<AlphaVector> valueFunction, double lambda, Random rnd) {
        this.pomdp = pomdp;
        this.lambda = lambda;
        this.rnd = rnd;
        
        // Extract Q-functions by grouping alpha vectors by action
        int nActions = pomdp.getNumActions();
        this.qFunctions = new ArrayList<>();
        
        // Initialise empty lists for each action
        for (int a = 0; a < nActions; a++) {
            qFunctions.add(new ArrayList<>());
        }
        
        // Group alpha vectors by action
        for (AlphaVector alpha : valueFunction) {
            int action = alpha.getAction();
            if (action >= 0 && action < nActions) {
                qFunctions.get(action).add(alpha);
            }
        }
        
        // Some actions might have empty Q-functions if no alpha vectors were assigned to them
    }
    
    /**
     * Select action at belief using softmax sampling
     */
    public int selectAction(BeliefPoint b) {
        return selectAction(b.getBelief());
    }
    
    /**
     * Select action at belief using softmax sampling
     */
    public int selectAction(double[] belief) {
        int nActions = qFunctions.size();

        System.out.println("[ERPolicy] Number of actions: " + nActions);

        // Compute Q-value for each action (max over alphas)
        double[] qValues = new double[nActions];
        for (int a = 0; a < nActions; a++) {
            qValues[a] = computeQValue(belief, qFunctions.get(a));
            System.out.println("[ERPolicy] Q-value for action " + a + ": " + qValues[a]);
        }

        // Compute softmax weights
        double[] weights = softmax(qValues);
        System.out.print("[ERPolicy] Softmax weights: ");
        for (int i = 0; i < weights.length; i++) {
            System.out.print(String.format("%.4f ", weights[i]));
        }
        System.out.println();

        // Sample from categorical distribution
        int selectedAction = sampleCategorical(weights);
        System.out.println("[ERPolicy] Selected action: " + selectedAction + " (prob=" + String.format("%.4f", weights[selectedAction]) + ")");

        return selectedAction;
    }
    
    /**
     * Get action probabilities at belief
     */
    public double[] getActionProbabilities(double[] belief) {
        int nActions = qFunctions.size();
        
        double[] qValues = new double[nActions];
        for (int a = 0; a < nActions; a++) {
            qValues[a] = computeQValue(belief, qFunctions.get(a));
        }
        
        return softmax(qValues);
    }
    
    /**
     * Compute Q-value at belief: max over alpha vectors
     */
    private double computeQValue(double[] belief, List<AlphaVector> Gamma) {
        double maxVal = Double.NEGATIVE_INFINITY;
        for (AlphaVector alpha : Gamma) {
            double val = alpha.getDotProduct(belief);
            if (val > maxVal) maxVal = val;
        }
        return maxVal;
    }
    
    /**
     * Compute softmax weights with temperature
     */
    private double[] softmax(double[] x) {
        double[] result = new double[x.length];
        
        // Scale by temperature
        double[] scaled = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            scaled[i] = x[i] / lambda;
        }
        
        // Find max for numerical stability
        double max = Double.NEGATIVE_INFINITY;
        for (double v : scaled) {
            if (v > max) max = v;
        }
        
        // Compute exp and sum
        double sum = 0.0;
        for (int i = 0; i < scaled.length; i++) {
            result[i] = Math.exp(scaled[i] - max);
            sum += result[i];
        }
        
        // Normalize
        for (int i = 0; i < result.length; i++) {
            result[i] /= sum;
        }
        
        return result;
    }
    
    /**
     * Sample from categorical distribution defined by weights
     */
    private int sampleCategorical(double[] weights) {
        double r = rnd.nextDouble();
        double cumSum = 0.0;
        
        for (int i = 0; i < weights.length; i++) {
            cumSum += weights[i];
            if (r <= cumSum) {
                return i;
            }
        }
        
        return weights.length - 1;  // Fallback
    }
    
    /**
     * Get best action (greedy, for evaluation)
     */
    public int getBestAction(double[] belief) {
        double[] probs = getActionProbabilities(belief);
        int best = 0;
        double bestProb = probs[0];
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > bestProb) {
                bestProb = probs[i];
                best = i;
            }
        }
        return best;
    }
}

