package it.gamma;

public class Gamma {
    // Error Prone: ReferenceEquality - reference equality used to compare strings
    public boolean isGamma(final String value) {
        return value == "gamma";
    }
}
