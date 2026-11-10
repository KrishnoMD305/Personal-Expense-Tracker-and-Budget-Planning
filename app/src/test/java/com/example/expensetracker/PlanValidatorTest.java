package com.example.expensetracker;

import static com.example.expensetracker.TestDays.d;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

public class PlanValidatorTest {
    private TimeZone saved;
    private long TODAY;

    @Before
    public void setUp() {
        saved = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        TODAY = d(2026, 10, 17);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(saved);
    }

    @Test
    public void nameRules() {
        assertNotNull(PlanValidator.validateName(null));
        assertNotNull(PlanValidator.validateName("   "));
        assertNull(PlanValidator.validateName("  Laptop "));
        assertNull(PlanValidator.validateName(new String(new char[PlanValidator.MAX_NAME]).replace('\0', 'a')));
        assertNotNull(PlanValidator.validateName(new String(new char[PlanValidator.MAX_NAME + 1]).replace('\0', 'a')));
    }

    @Test
    public void goalDeadline() {
        assertNull(PlanValidator.validateDeadline(0, TODAY, true));                       // none is fine
        assertNull(PlanValidator.validateDeadline(TODAY, TODAY, true));                   // today is fine
        assertNotNull(PlanValidator.validateDeadline(d(2026, 10, 16), TODAY, true));      // new + past
        assertNull(PlanValidator.validateDeadline(d(2026, 10, 16), TODAY, false));        // unchanged old deadline
    }

    @Test
    public void recurringDates() {
        assertNotNull(PlanValidator.validateRuleDates(0, 0, TODAY, true));
        assertNull(PlanValidator.validateRuleDates(TODAY, 0, TODAY, true));
        assertNotNull(PlanValidator.validateRuleDates(d(2026, 10, 16), 0, TODAY, true));
        assertNull(PlanValidator.validateRuleDates(d(2026, 10, 1), 0, TODAY, false));     // existing rule, start unchanged
        assertNull(PlanValidator.validateRuleDates(TODAY, TODAY, TODAY, true));           // end == start
        assertNotNull(PlanValidator.validateRuleDates(TODAY, d(2026, 10, 16), TODAY, true));
    }

    @Test
    public void billFields() {
        assertNotNull(PlanValidator.validateDueDate(0));
        assertNull(PlanValidator.validateDueDate(TODAY));
        assertNull(PlanValidator.validateReminderDays(0));
        assertNull(PlanValidator.validateReminderDays(PlanValidator.MAX_REMINDER_DAYS));
        assertNotNull(PlanValidator.validateReminderDays(-1));
        assertNotNull(PlanValidator.validateReminderDays(PlanValidator.MAX_REMINDER_DAYS + 1));
        assertEquals("Name is required", PlanValidator.validateName(""));
    }
}
