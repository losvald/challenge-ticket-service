package work.losvald;

import static org.junit.Assert.*;
import static work.losvald.TicketService.*;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class ThreePassGreedyTicketServiceTest {

  // Mock SublinearGreedyTicketService in a way that provides
  // efficient and direct way of testing its allocator -- the only new
  // piece of logic that it adds.  Note that everything else is
  // _already covered_ by BaseTicketServiceTest.
  //
  // Therefore, mock the clock so that exactly one hold per ms occurs,
  // and adds a method expire(H) that expires up to H earliest holds.

  static class MockedTicketService extends ThreePassGreedyTicketService {
    public MockedTicketService(int numRows, int numCols, Duration holdExpiration) {
      super(numRows, numCols, holdExpiration);
      clock = Clock.fixed(java.time.Instant.EPOCH, java.time.Clock.systemUTC().getZone());
    }

    @Override
    public SeatHold findAndHoldSeats(int numSeats, String customerEmail) {
      SeatHold ret = super.findAndHoldSeats(numSeats, customerEmail);
      holdCount++;
      if (tickCredit)
        tickCredit = false;
      else
        tickClock();
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
        tickCredit = true;
        numSeatsAvailable();  // trigger expiration
      }
      assertEquals("sanity check", count, holdCount0 - holdCount);
      return count;
    }

    private void tickClock() {
      clock = Clock.offset(clock, Duration.ofSeconds(1));
    }

    private int holdCount = 0;
    private boolean tickCredit = false;

    // Proxies for convenient holding/reservation of seats

    SeatHold hold(int numSeats) {
      return findAndHoldSeats(numSeats, PROXY_EMAIL);
    }

    SeatHold reserve(int numSeats) {
      SeatHold seatHold = hold(numSeats);
      reserveSeats(seatHold.getId(), PROXY_EMAIL);
      return seatHold;
    }

    private static final String PROXY_EMAIL = "hold.or.reserve@proxy.me";
  }

  @Test
  public void testStage1x7() {
    MockedTicketService svc = new MockedTicketService(1, 7, Duration.ofSeconds(10));

    assertEquals("0:0-1", svc.hold(2).hashSeats());
    assertEquals("0:2-5", svc.reserve(4).hashSeats());

    svc.expire(1);  // expire 0:0-1
    assertEquals("0:0-1,6", svc.hold(3).hashSeats());

    svc.expire(1);  // expire 0:0-1,6 (0:2-5 are already reserved)
    assertEquals("0:0", svc.hold(1).hashSeats());
    assertEquals("0:1,6", svc.hold(2).hashSeats());
  }

  @Test
  public void testStage4x5() {
    MockedTicketService svc = new MockedTicketService(4, 5, Duration.ofSeconds(20));

    assertEquals("0:0-3", svc.hold(4).hashSeats());
    assertEquals("1:0-2", svc.hold(3).hashSeats());
    assertEquals("2:0-4", svc.hold(2 + 2 + 1).hashSeats());
    assertEquals("3:0-3", svc.hold(4).hashSeats());
    // aaaa.
    // bbb..
    // ccccc
    // dddd.

    assertEquals("0:4|1:3-4|3:4", svc.reserve(2 + 1 + 1).hashSeats());
    svc.expire(3);  // expire 0:0-4 (a), 1:0-3 (b), 2:0-4 (c)
    // ....E
    // ...EE
    // .....
    // ddddE

    assertEquals("0:0-3|1:0-1|2:0-3", svc.hold(2 * 5).hashSeats());
    assertEquals("1:2|2:4", svc.hold(1 + 1).hashSeats());
    // ffffE
    // ffgEE
    // ffffg
    // ddddE
  }

  @Test
  public void testStage2x10InterleavedSameRow() {
    MockedTicketService svc = new MockedTicketService(2, 10, Duration.ofSeconds(10));

    assertEquals("0:0-1", svc.reserve(2).hashSeats());
    assertEquals("0:2-3", svc.hold(2).hashSeats());
    assertEquals("0:4-5", svc.reserve(2).hashSeats());
    assertEquals("0:6-7", svc.hold(2).hashSeats());
    assertEquals("0:8", svc.reserve(1).hashSeats());
    svc.expire(2);
    // AA..CC..D.
    // ..........

    assertEquals("1:0-5", svc.reserve(6).hashSeats());
    assertEquals("0:2-3,6-7,9", svc.hold(5).hashSeats());
    // AAffCCffDf
    // EEEEEE....
  }
}
