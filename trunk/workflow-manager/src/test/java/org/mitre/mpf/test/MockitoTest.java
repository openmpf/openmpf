package org.mitre.mpf.test;

import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public interface MockitoTest {

    public abstract static class Strict {
        @Rule
        public MockitoRule _mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);
    }

    public abstract static class Lenient {
        @Rule
        public MockitoRule _mockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);
    }
}
