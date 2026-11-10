package com.example.expensetracker;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BudgetValidatorTest {
    @Test
    public void categoryBudgetMustBePositive() {
        assertNotNull(BudgetValidator.validateCategoryBudget(0, 0, 0));
        assertNotNull(BudgetValidator.validateCategoryBudget(-5, 0, 0));
    }

    @Test
    public void categoryBudgetWithoutOverallIsUnrestricted() {
        assertNull(BudgetValidator.validateCategoryBudget(500_000, 900_000, 0));
    }

    @Test
    public void categoryBudgetsMayEqualButNotExceedOverall() {
        assertNull(BudgetValidator.validateCategoryBudget(4_000, 6_000, 10_000));
        String err = BudgetValidator.validateCategoryBudget(4_001, 6_000, 10_000);
        assertNotNull(err);
        assertTrue(err.contains("monthly budget"));
    }

    @Test
    public void tooLargeAmountRejected() {
        assertNotNull(BudgetValidator.validateCategoryBudget(MoneyUtils.MAX_PAISA + 1, 0, 0));
        assertNotNull(BudgetValidator.validateOverallBudget(MoneyUtils.MAX_PAISA + 1, 0));
    }

    @Test
    public void overallCannotDropBelowCategoryTotal() {
        assertNull(BudgetValidator.validateOverallBudget(10_000, 10_000));
        assertNull(BudgetValidator.validateOverallBudget(10_000, 0));
        assertNotNull(BudgetValidator.validateOverallBudget(9_999, 10_000));
        assertNotNull(BudgetValidator.validateOverallBudget(0, 0));
    }

    @Test
    public void amountInputParsingRejectsBadValues() {
        String[] bad = {"", "   ", "abc", "0", "-5", "1.234", "1e3x", "99999999999999"};
        for (String s : bad) {
            try {
                MoneyUtils.parseToPaisa(s);
                org.junit.Assert.fail("should reject: " + s);
            } catch (MoneyUtils.InvalidAmountException expected) {
                // ok
            }
        }
    }

    @Test
    public void amountInputParsingAcceptsGoodValues() throws Exception {
        org.junit.Assert.assertEquals(125050, MoneyUtils.parseToPaisa("1,250.50"));
        org.junit.Assert.assertEquals(500, MoneyUtils.parseToPaisa("5"));
        org.junit.Assert.assertEquals(1, MoneyUtils.parseToPaisa("0.01"));
        org.junit.Assert.assertEquals(MoneyUtils.MAX_PAISA, MoneyUtils.parseToPaisa("999999999.99"));
    }
}
