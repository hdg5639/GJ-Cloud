package gj.cloud.user.domain.plan.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanTypeTest {

    @Test
    void exposesPlanResourceLimits() {
        assertEquals(4, PlanType.FREE.getRamGb());
        assertEquals(10, PlanType.PRO.getRamGb());
    }
}
