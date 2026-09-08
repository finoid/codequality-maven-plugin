package it.beta;

import it.alpha.Alpha;

public class Beta implements Runnable {
    private boolean ran;

    // Error Prone: MissingOverride - implements a method of Runnable without @Override
    public void run() {
        ran = new Alpha().sameArray(new int[0], new int[0]);
    }

    public boolean hasRun() {
        return ran;
    }
}
