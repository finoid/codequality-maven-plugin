package io.github.finoid.maven.plugins.codequality.fixtures;

import net.datafaker.Faker;
import net.datafaker.providers.base.Address;
import net.datafaker.providers.base.Color;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A utility class for generating random data of various types.
 * <p>
 * This class provides methods to generate random numbers, strings, enums, dates,
 * and objects using {@link Random} and {@link Faker}.
 * <p>
 * It can be used for testing, data seeding, or generating placeholder data.
 */
public final class RandomGenerator {
    private final Random random;
    private final Faker faker;

    private RandomGenerator(final Random random) {
        this.random = random;
        this.faker = new Faker(Locale.forLanguageTag("sv"), random);
    }

    /**
     * Creates a new instance of {@link RandomGenerator} with a default seed.
     *
     * @return a new {@code RandomGenerator} instance
     */
    public static RandomGenerator create() {
        return of(new Random(1));
    }

    /**
     * Creates a new instance of {@link RandomGenerator} with a custom {@link Random} instance.
     *
     * @param random the {@code Random} instance to use
     * @return a new {@code RandomGenerator} instance
     */
    public static RandomGenerator of(final Random random) {
        return new RandomGenerator(random);
    }

    /**
     * Returns a random enum value from the given enum class.
     *
     * @param clazz the enum class
     * @param <T>   the type of enum
     * @return a random enum value
     */
    public <T extends Enum<?>> T randomEnum(final Class<T> clazz) {
        final int index = nextInt(0, clazz.getEnumConstants().length);

        return clazz.getEnumConstants()[index];
    }

    /**
     * Returns a random element from the given collection.
     *
     * @param collection the collection to pick from
     * @param <T>        the type of elements in the collection
     * @return a random element from the collection
     */
    public <T, C extends Collection<T>> T randomIn(final C collection) {
        final List<T> list = new ArrayList<>(collection);

        return list.get(nextInt(0, list.size()));
    }

    /**
     * Returns a random element from the given array.
     *
     * @param items the array of items
     * @param <T>   the type of elements
     * @return a random element from the array
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <T> T randomIn(final T... items) {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("The items array must contain at least one element.");
        }

        final List<T> list = Arrays.asList(items);

        return list.get(nextInt(0, list.size()));
    }

    /**
     * Returns a random integer within the specified range.
     *
     * @param startInclusive the inclusive lower bound
     * @param endExclusive   the exclusive upper bound
     * @return a random integer within the range
     */
    public int nextInt(final int startInclusive, final int endExclusive) {
        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + random.nextInt(endExclusive - startInclusive);
    }

    /**
     * Returns a random long value within the specified range.
     *
     * @param startInclusive the inclusive lower bound
     * @param endExclusive   the exclusive upper bound
     * @return a random long value within the range
     */
    public long nextLong(final long startInclusive, final long endExclusive) {
        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + random.nextLong(endExclusive - startInclusive);
    }

    /**
     * Returns a random {@link BigInteger} within the specified range.
     *
     * @param startInclusive the inclusive lower bound
     * @param endExclusive   the exclusive upper bound
     * @return a random {@code BigInteger} within the range
     */
    public BigInteger nextBigInteger(final long startInclusive, final long endExclusive) {
        if (startInclusive == endExclusive) {
            return BigInteger.valueOf(startInclusive);
        }

        return BigInteger.valueOf(startInclusive + random.nextLong(endExclusive - startInclusive));
    }

    /**
     * Returns a random double value within the specified range.
     *
     * @param startInclusive the inclusive lower bound
     * @param endExclusive   the exclusive upper bound
     * @return a random double value within the range
     */
    public double nextDouble(final double startInclusive, final double endExclusive) {
        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + random.nextDouble(endExclusive - startInclusive);
    }

    /**
     * Returns a random float value within the specified range.
     *
     * @param startInclusive the inclusive lower bound
     * @param endExclusive   the exclusive upper bound
     * @return a random float value within the range
     */
    public float nextFloat(final float startInclusive, final float endExclusive) {
        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + random.nextFloat(endExclusive - startInclusive);
    }

    /**
     * Returns a random {@link BigDecimal} within the specified range.
     *
     * @param startInclusive the inclusive lower bound
     * @param endExclusive   the exclusive upper bound
     * @return a random {@code BigDecimal} within the range
     */
    public BigDecimal nextBigDecimal(final long startInclusive, final long endExclusive) {
        if (startInclusive == endExclusive) {
            return BigDecimal.valueOf(startInclusive);
        }

        return BigDecimal.valueOf(startInclusive + random.nextLong(endExclusive - startInclusive));
    }

    /**
     * Generates a random numeric string with the specified length.
     *
     * @param count the length of the numeric string
     * @return a random numeric string
     */
    public String randomNumeric(final int count) {
        if (count <= 0) {
            return String.valueOf(0);
        }

        final int numeric = random.nextInt((9 * (int) Math.pow(10, count - 1)) - 1) + (int) Math.pow(10, count - 1);

        return String.valueOf(numeric);
    }

    /**
     * Generates a random numeric string within the given range.
     *
     * @param startInclusive the minimum value
     * @param endExclusive   the maximum value
     * @return a random numeric string within the range
     */
    public String randomNumeric(final int startInclusive, final int endExclusive) {
        return String.valueOf(nextInt(startInclusive, endExclusive));
    }

    /**
     * Returns a random boolean value.
     *
     * @return {@code true} or {@code false} randomly
     */
    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    /**
     * Generates a random {@link LocalDate} within a fixed range (from 1980 to 30 years ahead).
     *
     * @return a random {@code LocalDate}
     */
    public LocalDate randomLocalDate() {
        final LocalDate startingPoint = LocalDate.of(1980, 1, 1);

        return startingPoint.plusYears(nextInt(0, 30))
            .plusMonths(nextInt(0, 12))
            .plusDays(nextInt(0, 31));
    }

    /**
     * Generates a random {@link LocalDateTime}.
     *
     * @return a random {@code LocalDateTime}
     */
    public LocalDateTime randomLocalDateTime() {
        return randomLocalDate()
            .atTime(LocalTime.ofSecondOfDay(nextInt(0, 100)));
    }

    /**
     * Generates a random alphabetic string of the given length.
     *
     * @param count the length of the string
     * @return a random alphabetic string
     */
    public String randomAlphabetic(final int count) {
        return RandomStringUtils.random(count, 0, 0, true, false, null, random);
    }

    /**
     * Generates a random alphanumeric string of the given length.
     *
     * @param count the length of the string
     * @return a random alphanumeric string
     */
    public String randomAlphanumeric(final int count) {
        return RandomStringUtils.random(count, 0, 0, true, true, null, random);
    }

    /**
     * Generates a random name.
     *
     * @return a random name
     */
    public String randomName() {
        return faker.funnyName().name();
    }

    /**
     * Generates a random address.
     *
     * @return a random {@link Address}
     */
    public Address randomAddress() {
        return faker.address();
    }

    /**
     * Generates a random color.
     *
     * @return a random {@link Color}
     */
    public Color randomColor() {
        return faker.color();
    }
}