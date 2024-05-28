/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/


package org.mitre.mpf.wfm.camel.operations.mediainspection;

import org.apache.commons.math3.util.ArithmeticUtils;

// Using custom Fraction class instead of org.apache.commons.lang3.math.Fraction because it
// uses ints for the numerator and denominator. longs are needed because the values in
// the frame rate and time base can be large.
public record Fraction(long numerator, long denominator) implements Comparable<Fraction> {

    public Fraction {
        if (denominator < 0) {
            numerator *= -1;
            denominator *= -1;
        }

        long gcd = ArithmeticUtils.gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
    }

    public static Fraction parse(String fractionStr) throws NumberFormatException {
        var parts = fractionStr.split("/", 2);
        long numerator = Long.parseLong(parts[0]);
        boolean containsSlash = parts.length == 2;
        if (containsSlash) {
            return new Fraction(numerator, Long.parseLong(parts[1]));
        }
        else {
            return new Fraction(numerator, 1);
        }
    }


    public Fraction mul(Fraction other) {
        // Creating these new fractions so that they get reduced by the constructor.
        var f1 = new Fraction(numerator, other.denominator);
        var f2 = new Fraction(other.numerator, denominator);
        return new Fraction(f1.numerator * f2.numerator, f1.denominator * f2.denominator);
    }

    public Fraction mul(long value) {
        return mul(new Fraction(value, 1));
    }


    public Fraction add(Fraction other) {
        long lcm = ArithmeticUtils.lcm(denominator, other.denominator);
        long leftNumerator = lcm / denominator * numerator;
        long rightNumerator = lcm / other.denominator * other.numerator;
        return new Fraction(leftNumerator + rightNumerator, lcm);
    }


    public Fraction sub(Fraction other) {
        long lcm = ArithmeticUtils.lcm(denominator, other.denominator);
        long leftNumerator = lcm / denominator * numerator;
        long rightNumerator = lcm / other.denominator * other.numerator;
        return new Fraction(leftNumerator - rightNumerator, lcm);
    }


    @Override
    public int compareTo(Fraction other) {
        long n = sub(other).numerator;
        if (n > 0) {
            return 1;
        }
        else if (n < 0) {
            return -1;
        }
        else {
            return 0;
        }
    }


    public Fraction max(Fraction other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public Fraction min(Fraction other) {
        return compareTo(other) < 0 ? this : other;
    }

    public Fraction invert() {
        return new Fraction(denominator, numerator);
    }

    public double toDouble() {
        return ((double) numerator) / denominator;
    }

    public long roundUp() {
        return (long) Math.ceil(toDouble());
    }
}