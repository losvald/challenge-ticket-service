package work.losvald;

import static org.junit.Assert.*;
import static work.losvald.TicketService.*;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

public class SublinearGreedyTicketServiceTest {

  // Mock SublinearGreedyTicketService in a way that provides
  // efficient and direct way of testing its allocator -- the only new
  // piece of logic that it adds.  Note that everything else is
  // _already covered_ by BaseTicketServiceTest.
  //
  // Therefore, mock the clock so that exactly one hold per ms occurs,
  // and adds a method expire(H) that expires up to H earliest holds.

  static class MockedTicketService extends SublinearGreedyTicketService {
    public MockedTicketService(int numRows, int numCols, Duration holdExpiration) {
      super(numRows, numCols, holdExpiration);
      clock = Clock.fixed(java.time.Instant.EPOCH, java.time.Clock.systemUTC().getZone());
    }

    @Override
    public SeatHold findAndHoldSeats(int numSeats, String customerEmail) {
      SeatHold ret = super.findAndHoldSeats(numSeats, customerEmail);
      tickClock();
      holdCount++;
      return ret;
    }

    @Override
    public String reserveSeats(int seatHoldId, String customerEmail) {
      String ret = super.reserveSeats(seatHoldId, customerEmail);
      holdCount--;
      return ret;
    }

    /**
     * Adjusts the mocked clock to _earliest time_ that triggers the
     * expiration of _at most_ count seat holds.
     *
     * @return the count of expired seat holds in range [0, maxCount]
     */
    int expire(int count) {
      int holdCount0 = holdCount;
      if (count > holdCount0)
        count = holdCount0;
      while (holdCount > holdCount0 - count) {
        expiredHoldVisitor = (hold) -> holdCount--;
        tickClock();
        numSeatsAvailable();  // trigger expiration
      }
      assertEquals("sanity check", count, holdCount0 - holdCount);
      return count;
    }

    private void tickClock() {
      clock = Clock.offset(clock, Duration.ofMillis(1));
    }

    private int holdCount = 0;
  }

  @Test
  public void testMeta() {

  }
}
