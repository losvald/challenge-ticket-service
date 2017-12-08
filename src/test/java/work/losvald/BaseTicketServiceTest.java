package work.losvald;

import static org.junit.Assert.*;
import static work.losvald.TicketService.*;
import static work.losvald.TicketServiceTest.SimpleSeatLayout;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class BaseTicketServiceTest {
  static Clock epochTime() {
    return Clock.fixed(java.time.Instant.EPOCH, java.time.Clock.systemUTC().getZone());
  }

  static class MockedTicketService extends BaseTicketService {
    MockedTicketService(int numRows, int numCols, Duration holdExpiration) {
      super(new SimpleSeatLayout(numRows, numCols), holdExpiration);
      this.clock = epochTime();
    }

    protected Allocator createAllocator(SeatLayout layout) {
      return alloc = new DummyAllocator(layout);
    }

    DummyAllocator alloc;

    static class DummyAllocator extends Allocator {
      @Override
      public boolean allocate(int numSeats, SeatHold hold) {
        hold.addRange(layout, counter++, 1, numSeats);
        return true;
      }

      @Override
      public void release(SeatHold hold) {
          released.add(hold);
      }

      DummyAllocator(SeatLayout layout) { super(layout); }
      int counter = 0;
      List<SeatHold> released = new ArrayList<SeatHold>();
    }
  }

  @Test
  public void testHoldAllocationOnly() {
    TicketService svc = new MockedTicketService(3, 4, Duration.ofHours(1));
    SeatHold hold4 = svc.findAndHoldSeats(4, "john@doe.org");
    assertEquals(4, hold4.seatCount());

    SeatHold hold6 = svc.findAndHoldSeats(6, "foo@bar.Baez");
    assertEquals(6, hold6.seatCount());

    assertNull(svc.findAndHoldSeats(3, "too@many.seats"));
    SeatHold hold2 = svc.findAndHoldSeats(2, "all@remaining.seats");
    assertEquals(2, hold2.seatCount());

    assertNull(svc.findAndHoldSeats(1, "no@more.seats"));
  }

  @Test
  public void testIdGenerationAndReservation() {
    final String email1 = "customer@one.org", email2 = "customer@two.org";
    final Duration expirationPeriod = Duration.ofMinutes(5);
    MockedTicketService svc = new MockedTicketService(9, 9, expirationPeriod);
    int numSeatsAvailable0 = svc.numSeatsAvailable();

    // verify that _different_ customers at the _same millisecond_ get different IDs
    svc.clock = Clock.offset(epochTime(), Duration.ofMillis(1)); // = t0
    int id1 = svc.findAndHoldSeats(1, email1).getId();
    int id2 = svc.findAndHoldSeats(2, email2).getId();
    assertNotEquals(id1, id2);

    // verify that the _same_ customers at _different times_ gets different IDs
    svc.clock = Clock.offset(svc.clock, Duration.ofMillis(3)); // = t0 +3ms
    int id3 = svc.findAndHoldSeats(4, email1).getId();
    assertNotEquals("ID3=" + id3 + "==" + id1 + "=ID1", id3, id1);

    // verify that the _same_ customers at the _same time_ gets different IDs
    assertNotEquals(id3, svc.findAndHoldSeats(1, email1).getId());

    // verify that providing wrong email but correct ID returns null
    assertNull(svc.reserveSeats(id1, email2));
    assertNull(svc.reserveSeats(id2, email1));

    // verify that providing both correctly succeeds
    String confirmCode = svc.reserveSeats(id2, email2);
    assertEquals("CAEE4FB1-6E", confirmCode);

    // verify that the reserved seat group does _not expire_ when the hold ends
    svc.expiredHoldVisitor = (hold) -> assertNotEquals(id2, hold.getId());
    svc.clock = Clock.offset(svc.clock, expirationPeriod);
  }

  @Test
  public void testHoldExpiration() {
    BaseTicketService svc = new MockedTicketService(1, 2, Duration.ofSeconds(10));
    assertEquals(2, svc.numSeatsAvailable());

    svc.clock = Clock.offset(epochTime(), Duration.ofSeconds(0));
    SeatHold holdUntil10 = svc.findAndHoldSeats(1, "0..10@one.seat");
    assertEquals(1, svc.numSeatsAvailable());

    svc.clock = Clock.offset(svc.clock, Duration.ofSeconds(5));
    SeatHold holdUntil15 = svc.findAndHoldSeats(1, "5..15@one.seat");
    assertEquals(0, svc.numSeatsAvailable());
    assertEquals(null, svc.findAndHoldSeats(1, "5..15@no-more.seat"));

    // verify that calling findAndHoldSeats() triggers expiration (of 1 seat)
    svc.clock = Clock.offset(epochTime(), Duration.ofSeconds(12));
    SeatHold holdUntil22 = svc.findAndHoldSeats(1, "12..22@one.seat");
    assertNotEquals(null, holdUntil22);
    assertEquals(0, svc.numSeatsAvailable());

    // verify that calling numSeatsAvailable() also triggers expiration (x2)
    svc.clock = Clock.offset(epochTime(), Duration.ofSeconds(25));
    Set<SeatHold> expectedExpired = new HashSet<>();
    expectedExpired.add(holdUntil15);
    expectedExpired.add(holdUntil22);
    svc.expiredHoldVisitor =
        (hold) -> assertTrue(expectedExpired.remove(hold));
    assertEquals(2, svc.numSeatsAvailable());
    assertTrue(expectedExpired.isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFindAndHoldInvalidNumSeats() {
    BaseTicketService svc = new MockedTicketService(3, 3, Duration.ofSeconds(10));
    svc.findAndHoldSeats(0, "john@doe.org");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFindAndHoldNumSeatsExceedsMaximumInRoom() {
    BaseTicketService svc = new MockedTicketService(3, 3, Duration.ofHours(1));
    svc.findAndHoldSeats(3 * 3 + 1, "exceeds.both@available+unavailable.com");
  }

  @Test(expected = NullPointerException.class)
  public void testFindAndHoldNullEmail() {
    BaseTicketService svc = new MockedTicketService(3, 3, Duration.ofSeconds(10));
    svc.findAndHoldSeats(1, null);
  }
}
