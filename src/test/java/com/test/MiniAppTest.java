package com.test;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for MiniApp addition functionality
 */
public class MiniAppTest {

    @Test
    public void testAddPositiveNumbers() {
        int result = MiniApp.add(5, 10);
        assertEquals(15, result);
    }

    @Test
    public void testAddNegativeNumbers() {
        int result = MiniApp.add(-5, -10);
        assertEquals(-15, result);
    }

    @Test
    public void testAddPositiveAndNegative() {
        int result = MiniApp.add(10, -5);
        assertEquals(5, result);
    }

    @Test
    public void testAddWithZero() {
        int result = MiniApp.add(0, 10);
        assertEquals(10, result);

        result = MiniApp.add(10, 0);
        assertEquals(10, result);

        result = MiniApp.add(0, 0);
        assertEquals(0, result);
    }

    @Test
    public void testAddBoundaryValues() {
        // Test with large positive numbers that don't overflow
        int result = MiniApp.add(1000000, 2000000);
        assertEquals(3000000, result);

        // Test with large negative numbers that don't overflow
        result = MiniApp.add(-1000000, -2000000);
        assertEquals(-3000000, result);
    }

    @Test(expected = ArithmeticException.class)
    public void testAddOverflowPositive() {
        // This should cause overflow
        MiniApp.add(Integer.MAX_VALUE, 1);
    }

    @Test(expected = ArithmeticException.class)
    public void testAddOverflowNegative() {
        // This should cause overflow
        MiniApp.add(Integer.MIN_VALUE, -1);
    }

    @Test(expected = ArithmeticException.class)
    public void testAddOverflowLargePositives() {
        // This should cause overflow
        MiniApp.add(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void testAddNearMaxValueWithoutOverflow() {
        // This should NOT overflow
        int result = MiniApp.add(Integer.MAX_VALUE, 0);
        assertEquals(Integer.MAX_VALUE, result);
    }

    @Test
    public void testAddNearMinValueWithoutOverflow() {
        // This should NOT overflow
        int result = MiniApp.add(Integer.MIN_VALUE, 0);
        assertEquals(Integer.MIN_VALUE, result);
    }
}
