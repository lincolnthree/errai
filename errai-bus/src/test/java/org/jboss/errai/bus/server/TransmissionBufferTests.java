/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.bus.server;

import junit.framework.TestCase;
import org.jboss.errai.bus.client.tests.support.RandomProvider;
import org.jboss.errai.bus.server.io.buffers.BufferColor;
import org.jboss.errai.bus.server.io.buffers.TransmissionBuffer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Mike Brock
 */
public class TransmissionBufferTests extends TestCase {
  static {
    // make sure protocol provider is initialized;
  }

  public void testBufferWriteAndRead() {
    TransmissionBuffer buffer = TransmissionBuffer.createDirect();

    String s = "This is a test";

    BufferColor colorA = BufferColor.getNewColor();


    try {
      ByteArrayInputStream bInputStream = new ByteArrayInputStream(s.getBytes());
      buffer.write(s.length(), bInputStream, colorA);

      ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();
      buffer.read(bOutputStream, colorA);

      assertEquals(s, new String(bOutputStream.toByteArray()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testBufferCycle() throws IOException {
    TransmissionBuffer buffer = TransmissionBuffer.create(10, 10);

    BufferColor color = BufferColor.getNewColor();

    String s = "12345789012345";

    long start = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      ByteArrayInputStream bInputStream = new ByteArrayInputStream(s.getBytes());
      ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();

      buffer.write(s.length(), bInputStream, color);
      buffer.read(bOutputStream, color);

      assertEquals(s, new String(bOutputStream.toByteArray()));
    }
    System.out.println(System.currentTimeMillis() - start);
  }


  public void testColorInterleaving() throws IOException {
    TransmissionBuffer buffer = TransmissionBuffer.create(10, 20);

    BufferColor colorA = BufferColor.getNewColor();
    BufferColor colorB = BufferColor.getNewColor();
    BufferColor colorC = BufferColor.getNewColor();


    String stringA = "12345678";
    String stringB = "ABCDEFGH";
    String stringC = "IJKLMNOP";


    long start = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      ByteArrayInputStream bInputStream = new ByteArrayInputStream(stringA.getBytes());
      buffer.write(stringA.length(), bInputStream, colorA);

      bInputStream = new ByteArrayInputStream(stringB.getBytes());
      buffer.write(stringB.length(), bInputStream, colorB);

      bInputStream = new ByteArrayInputStream(stringC.getBytes());
      buffer.write(stringC.length(), bInputStream, colorC);


      ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();
      buffer.read(bOutputStream, colorA);
      assertEquals(stringA, new String(bOutputStream.toByteArray()));

      bOutputStream = new ByteArrayOutputStream();
      buffer.read(bOutputStream, colorB);
      assertEquals(stringB, new String(bOutputStream.toByteArray()));

      bOutputStream = new ByteArrayOutputStream();
      buffer.read(bOutputStream, colorC);
      assertEquals(stringC, new String(bOutputStream.toByteArray()));

    }
    System.out.println(System.currentTimeMillis() - start);
  }

  final static int COLOR_COUNT = 1;

  public void testAudited() throws Exception {

    final List<BufferColor> colors = new ArrayList<BufferColor>();

    final TransmissionBuffer buffer = TransmissionBuffer.create();

    for (int i = 0; i < COLOR_COUNT; i++) {
      colors.add(BufferColor.getNewColor());
    }
    final Random random = new Random(2234);

    final String[] writeString = {"<JIMMY>", "<CRAB>", "<KITTY>", "<DOG>", "<JONATHAN>"};


    final Map<Short, List<String>> writeLog = new HashMap<Short, List<String>>();

    final int createCount = 500;


    final AtomicInteger totalWrites = new AtomicInteger();

    List<String> results = Collections.synchronizedList(new ArrayList<String>());

    for (int i = 0; i < createCount; i++) {
      final BufferColor toContend = colors.get(random.nextInt(COLOR_COUNT));
      assertNotNull(toContend);
      new Runnable() {
        @Override
        public void run() {
          try {
            String toWrite = writeString[random.nextInt(writeString.length)];
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(toWrite.getBytes());
            buffer.write(toWrite.getBytes().length, byteArrayInputStream, toContend);
            totalWrites.incrementAndGet();

            List<String> stack = writeLog.get(toContend.getColor());
            if (stack == null) {
              writeLog.put(toContend.getColor(), stack = new ArrayList<String>());
            }

            stack.add(toWrite);

            System.out.println("Wrote color " + toContend.getColor() + ": " + toWrite + ". Total writes is now " + totalWrites);
          }
          catch (IOException e) {
            e.printStackTrace();
          }
        }
      }.run();
    }

    assertEquals(createCount, totalWrites.intValue());

    AtomicInteger resultSequenceNumber = new AtomicInteger();

    for (int i = 0; i < COLOR_COUNT; i++) {
      resultSequenceNumber.incrementAndGet();

      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byteArrayOutputStream.reset();
      assertEquals(0, byteArrayOutputStream.size());
      buffer.read(byteArrayOutputStream, colors.get(i));
      assertTrue("Expected >0 bytes; got " + byteArrayOutputStream.size(), byteArrayOutputStream.size() > 0);

      String val = new String(byteArrayOutputStream.toByteArray());
      results.add(val);

      List<String> buildResultList = new ArrayList<String>();

      int st = 0;
      for (int c = 0; c < val.length(); c++) {
        switch (val.charAt(c)) {
          case '>':
            c++;
            buildResultList.add(val.substring(st, st = c));
        }
      }


      List<String> resultList = new ArrayList<String>(buildResultList);
      List<String> log = new ArrayList<String>(writeLog.get(colors.get(i).getColor()));

      while (!log.isEmpty() && !resultList.isEmpty()) {
        String nm = log.remove(0);
        String test = resultList.remove(0);
        if (!nm.equals(test)) {
          System.out.println("[" + resultSequenceNumber + "] expected : " + nm + " -- but found: " + test
                  + " (color: " + colors.get(i).getColor() + ")");

          System.out.println("  --> log: " + writeLog.get(colors.get(i).getColor()) + " vs result: " + buildResultList);
        }
      }


      if (!log.isEmpty())
        System.out.println("[" + resultSequenceNumber + "] results have missing items: " + log
                + " (color: " + colors.get(i).getColor() + ")");

      if (!resultList.isEmpty())
        System.out.println("[" + resultSequenceNumber + "] results contain items not logged: " + resultList
                + " (color: " + colors.get(i).getColor() + ")");
    }

    assertEquals(COLOR_COUNT, results.size());

    int count = 0;
    for (String res : results) {
      for (int i = 0; i < res.length(); i++) {
        if (res.charAt(i) == '<') count++;
      }

      System.out.println();
      System.out.print(res);
    }


    buffer.dumpSegments(new PrintWriter(System.out));

    assertEquals(createCount, count);
  }


  public void testLargeOversizedSegments() {
    final TransmissionBuffer buffer = TransmissionBuffer.create();

    final BufferColor colorA = BufferColor.getNewColor();

    RandomProvider random = new RandomProvider() {
      private char[] CHARS = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
              'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'};

      private Random random = new Random(System.nanoTime());

      public boolean nextBoolean() {
        return random.nextBoolean();
      }

      public int nextInt(int upper) {
        return random.nextInt(upper);
      }

      public double nextDouble() {
        return new BigDecimal(random.nextDouble(), MathContext.DECIMAL32).doubleValue();
      }

      public char nextChar() {
        return CHARS[nextInt(1000) % CHARS.length];
      }

      public String randString() {
        StringBuilder builder = new StringBuilder();
        int len = nextInt(25) + 5;
        for (int i = 0; i < len; i++) {
          builder.append(nextChar());
        }
        return builder.toString();
      }
    };

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 1024 * 10; i++) {
      builder.append(random.nextChar());
    }

    String s = builder.toString();

    ByteArrayInputStream bInputStream = new ByteArrayInputStream(s.getBytes());
    ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();

    try {
      for (int i = 0; i < 10000; i++) {
        bInputStream.reset();
        buffer.write(s.length(), bInputStream, colorA);

        bOutputStream.reset();
        buffer.read(bOutputStream, colorA);
        assertEquals(s, new String(bOutputStream.toByteArray()));
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  final static int SEGMENT_COUNT = 10;


  /**
   * ******
   *
   * @throws Exception
   */

  public void testMultithreadedBufferUse() throws Exception {
    File logFile = new File("multithread_test.log");
    File rawBufferFile = new File("raw_buffer.log");
    if (!logFile.exists()) logFile.createNewFile();
    if (!rawBufferFile.exists()) rawBufferFile.createNewFile();

    final TransmissionBuffer buffer = TransmissionBuffer.createDirect(32, 32000);

    OutputStream fileLog = new BufferedOutputStream(new FileOutputStream(logFile, false));
    OutputStream rawBuffer = new BufferedOutputStream(new FileOutputStream(rawBufferFile, false));

    final PrintWriter logWriter = new PrintWriter(fileLog);

    logWriter.println("START SESSION: " + new Date().toString());
    try {
      final List<BufferColor> segs = new ArrayList<BufferColor>();
      for (int i = 0; i < SEGMENT_COUNT; i++) {
        segs.add(BufferColor.getNewColor());
      }

      final Collection<String> writeAuditLog = new ConcurrentLinkedQueue<String>();
      final Collection<String> readAuditLog = new ConcurrentLinkedQueue<String>();

      final int createCount = 10000;
      final String[] writeString = new String[createCount];

      for (int i = 0; i < createCount; i++) {
        writeString[i] = "<:::" + i + ":::>";
      }

      ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(10);

      //  logWriter.print("SESSION NUMBER " + outerCount);

      System.out.println("Running multi-threaded stress test ...");

      writeAuditLog.clear();
      readAuditLog.clear();

      final AtomicInteger totalWrites = new AtomicInteger();
      final AtomicInteger totalReads = new AtomicInteger();

      final CountDownLatch latch = new CountDownLatch(createCount);

      class TestReader {
        volatile boolean running = true;

        public void read(BufferColor color, boolean wait) throws Exception {
          ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          if (wait) {
            buffer.readWait(TimeUnit.SECONDS, 1, byteArrayOutputStream, color);
          }
          else {
            buffer.read(byteArrayOutputStream, color);
          }

          String val = new String(byteArrayOutputStream.toByteArray()).trim();
          List<String> buildResultList = new ArrayList<String>();

          logWriter.println(val);

          int st = 0;
          for (int c = 0; c < val.length(); c++) {
            switch (val.charAt(c)) {
              case '>': {
                buildResultList.add(val.substring(st, st = (c + 1)));
              }
            }
          }

          if (st < val.length()) {
            fail("malformed data: {{" + val + "}} length wrong: (waitread:" + wait + ")");
          }

          if (val.length() > 0 && val.charAt(val.length() - 1) != '>') {
            fail("malformed data: {{" + val + "}} (waitread:" + wait + ")");
          }

          boolean match;
          for (String s : buildResultList) {
            match = false;
            for (String testString : writeString) {
              if (s.equals(testString)) {
                totalReads.incrementAndGet();
                match = true;
              }
            }
            assertTrue("unrecognized test string: {{" + s + "}}", match);
          }

          readAuditLog.addAll(buildResultList);
        }
      }

      final TestReader testReader = new TestReader();

      final Thread[] readers = new Thread[SEGMENT_COUNT];
      for (int i = 0; i < SEGMENT_COUNT; i++) {
        final int item = i;

        readers[i] = new Thread() {
          final BufferColor color = segs.get(item);

          @Override
          public void run() {
            try {
              while (testReader.running) {
                testReader.read(color, true);
              }
            }
            catch (Throwable t) {
              t.printStackTrace();
            }
          }
        };

        readers[i].start();
      }

      for (int i = 0; i < createCount; i++) {
        final int item = i;

        exec.execute(new Runnable() {
          @Override
          public void run() {
            try {
              String toWrite = writeString[item];
              writeAuditLog.add(toWrite);

              ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(toWrite.getBytes());
              buffer.write(toWrite.length(), byteArrayInputStream, segs.get(item % SEGMENT_COUNT));

              totalWrites.incrementAndGet();
              latch.countDown();
            }
            catch (Throwable e) {
              e.printStackTrace();
            }
          }
        });
      }

      /**
       * Wait a maximum of 20 seconds.
       */
      latch.await(30, TimeUnit.SECONDS);

      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));

      testReader.running = false;
      for (Thread t : readers) {
        t.join();
      }

      exec.shutdownNow();

      if (totalWrites.intValue() != totalReads.intValue()) {
        /**
         * Double check that there isn't anything un-read.
         */

        LockSupport.parkNanos(100000);

        for (int i = 0; i < SEGMENT_COUNT; i++) {
          try {
            testReader.read(segs.get(i), false);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }

        if (totalWrites.intValue() != totalReads.intValue()) {
          //      new ReadWriteOrderAnalysis().analyze();

          System.out.println("-----");

          System.out.println("different number of reads and writes (writes=" + totalWrites + ";reads=" + totalReads + ")");
        }
      }

      System.out.println("Read / Write Symmetry Analysis ... ");
      for (String s : writeAuditLog) {
        if (!readAuditLog.contains(s)) {
          Collection<String> leftDiff = new ArrayList<String>(writeAuditLog);
          leftDiff.removeAll(readAuditLog);

          Collection<String> rightDiff = new ArrayList<String>(readAuditLog);
          rightDiff.removeAll(writeAuditLog);

          Set<String> uniqueReads = new HashSet<String>(readAuditLog);

          List<String> duplicates = new ArrayList<String>(readAuditLog);
          if (uniqueReads.size() < readAuditLog.size()) {
            for (String str : uniqueReads) {
              duplicates.remove(duplicates.indexOf(str));
            }
          }

          System.out.println("duplicates: " + duplicates);

          //    new ReadWriteOrderAnalysis().analyze();

          fail(s + " was written, but never read (leftDiff=" + leftDiff + ";rightDiff=" + rightDiff
                  + ";duplicatesInReadLog=" + duplicates + ")");
        }
      }

      System.out.println("Done.\n");
    }
    finally {
      buffer.dumpSegments(logWriter);

      logWriter.flush();

      fileLog.flush();
      fileLog.close();

      rawBuffer.flush();
      rawBuffer.close();
    }
  }

  public void testMultiThreadedManyTimes() throws Exception {
    for (int i = 0; i < 5; i++) {
      testMultithreadedBufferUse();
    }
  }


  public void testGloballyVisibleColors() throws IOException {
    BufferColor colorA = BufferColor.getNewColor();
    BufferColor colorB = BufferColor.getNewColor();

    BufferColor globalColor = BufferColor.getAllBuffersColor();

    TransmissionBuffer buffer = TransmissionBuffer.create(5, 2500);

    String stringA = "12345678";
    String stringB = "ABCDEFGH";
    String stringC = "IJKLMNOP";


    long start = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      ByteArrayInputStream bInputStream = new ByteArrayInputStream(stringA.getBytes());
      buffer.write(stringA.length(), bInputStream, colorA);

      bInputStream = new ByteArrayInputStream(stringB.getBytes());
      buffer.write(stringB.length(), bInputStream, colorB);

      bInputStream = new ByteArrayInputStream(stringC.getBytes());
      buffer.write(stringC.length(), bInputStream, globalColor);

      ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();
      buffer.read(bOutputStream, colorA);
      assertEquals(stringA + stringC, new String(bOutputStream.toByteArray()));

      bOutputStream = new ByteArrayOutputStream();
      buffer.read(bOutputStream, colorB);
      assertEquals(stringB + stringC, new String(bOutputStream.toByteArray()));

    }
    System.out.println(System.currentTimeMillis() - start);
  }

  public static String createGiantString() {
    int size = TransmissionBuffer.DEFAULT_SEGMENT_SIZE * 3;
    StringBuilder sb = new StringBuilder(size + 10);
    int i = 0;
    while (sb.length() < size) {
      sb.append(String.format("%10d,", i++));
    }
    return sb.toString();
  }

  public void testExtremelyLargeSegmentsInterleavedWithSmallSegments() throws IOException {
    TransmissionBuffer buffer = TransmissionBuffer.create();

    BufferColor globalColor = BufferColor.getAllBuffersColor();

    BufferColor red = BufferColor.getNewColor();

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < 1; i++) {
      String s = createGiantString();
      sb.append(s);
      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());

      buffer.write(byteArrayInputStream.available(), byteArrayInputStream, globalColor);
    }

    String s = "this is a short string";
    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
    sb.append(s);

    buffer.write(byteArrayInputStream.available(), byteArrayInputStream, globalColor);

    byteArrayInputStream.reset();

    StringBuilder out = new StringBuilder();

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    // for (int i = 0; i < 5; i++) {
    buffer.read(byteArrayOutputStream, red);
    // }

    for (byte b : byteArrayOutputStream.toByteArray()) {
      out.append((char) b);
    }

    assertEquals(sb.toString(), out.toString());
  }

  public void testContendedReads() throws IOException, InterruptedException {
    final TransmissionBuffer buffer = TransmissionBuffer.create();

    BufferColor globalColor = BufferColor.getAllBuffersColor();

    final BufferColor red = BufferColor.getNewColor();


    Thread[] threads = new Thread[2];
    class Runstatus {
      boolean run = true;
    }

    final Runstatus runstatus = new Runstatus();


    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread() {
        @Override
        public void run() {
          try {
            while (runstatus.run) {
              ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

              buffer.readWait(TimeUnit.MILLISECONDS, 100, byteArrayOutputStream, red);

              StringBuilder out = new StringBuilder();

              for (byte b : byteArrayOutputStream.toByteArray()) {
                out.append((char) b);
              }

            }
          }
          catch (Throwable t) {
            t.printStackTrace();
          }
        }
      };
    }

    for (Thread thread : threads) {
      thread.start();
    }

    String s = "this is a short string";
    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
    buffer.write(byteArrayInputStream.available(), byteArrayInputStream, red);

    Thread.sleep(2000);

    runstatus.run = false;

    for (Thread thread : threads) {
      thread.join();
    }
  }

  private static class TestInputStream extends InputStream {
    int count = 0;

    @Override
    public int read() throws IOException {
      if (++count > 201) {
        throw new AssertionError("buffer should have overflown");
      }

      return 0x01;
    }

    @Override
    public int available() throws IOException {
      return 200;
    }

  }

  public void testBufferOverFlowCondition() throws IOException {
    final TransmissionBuffer buffer = TransmissionBuffer.create(100, 2);

    buffer.write(new TestInputStream(), BufferColor.getAllBuffersColor());
    buffer.write(new TestInputStream(), BufferColor.getAllBuffersColor());

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      buffer.read(byteArrayOutputStream, BufferColor.getAllBuffersColor());
    }
    catch (IOException e) {
      assertTrue(e.getMessage().contains("overflow"));
      return;
    }

    fail("should have overflowed");

  }
}
