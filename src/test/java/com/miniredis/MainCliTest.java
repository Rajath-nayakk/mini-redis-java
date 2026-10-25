package com.miniredis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class MainCliTest {

    @Test
    void testHelpFlagPrintsUsageAndExitsCleanly() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outContent));
            Main.main(new String[]{"--help"});
            String output = outContent.toString();
            assertTrue(output.contains("MiniRedis"));
            assertTrue(output.contains("Usage:"));
            assertTrue(output.contains("--port"));
            assertTrue(output.contains("--help"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testShortHelpFlagPrintsUsage() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outContent));
            Main.main(new String[]{"-h"});
            String output = outContent.toString();
            assertTrue(output.contains("Usage:"));
            assertTrue(output.contains("--requirepass"));
        } finally {
            System.setOut(originalOut);
        }
    }
}
