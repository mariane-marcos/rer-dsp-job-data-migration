package br.car.dsp_batch.batch.config.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChangeDetectionStrategyTest {

	private final DefaultChangeDetectionStrategy strategy = new DefaultChangeDetectionStrategy();

	@Test
	void normalizeId_ShouldMakeNumericSourceMatchVarcharTarget() {
		Object fromSourceInteger = strategy.normalizeId(1);
		Object fromSourceLong = strategy.normalizeId(1L);
		Object fromTargetVarchar = strategy.normalizeId("1");

		assertEquals(fromTargetVarchar, fromSourceInteger);
		assertEquals(fromTargetVarchar, fromSourceLong);
		assertTrue(fromSourceInteger instanceof String);
	}

	@Test
	void normalizeId_ShouldKeepAlphanumericIds() {
		assertEquals("DF", strategy.normalizeId("DF"));
		assertEquals("DF", strategy.normalizeId(" DF "));
	}

	@Test
	void normalizeId_ShouldHandleBigDecimalAndNull() {
		assertEquals("5", strategy.normalizeId(new BigDecimal("5")));
		assertNull(strategy.normalizeId(null));
	}
}
