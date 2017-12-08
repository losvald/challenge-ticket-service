package work.losvald;

import static work.losvald.TicketService.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Objects;
import work.losvald.BaseTicketService.Allocator;
import work.losvald.TicketService.SeatHold;
import work.losvald.TicketService.SeatLayout;
import java.util.logging.Logger;

/**
 * A basic ticket service that implements secure reservation but does
 * not try to optimize the seats (supposed to be done by a subclass).
 *
 * This class is thread-safe and synchronizes accesses to the instance
 * created by createAllocator() for convenience, which is responsible
 * for seat assignment/tracking but not counting.  Hence, different
 * seat assignment algorithms (i.e., allocators) can be plugged in
 * without rewriting the bookkeeping logic done by BaseTicketService:
 * - enerating unique hold IDs in a cryptographically secure way
 * - mapping hold IDs to customer's email and seats
 * - expiring hold IDs
 * - generating a unique and traceable reservation numbers (perfect hash)
 *
 * This class has optimal asymptotic complexity under the current
 * semantics; each operation runs in amortized _constant time_,
 * excluding the time taken by the (abstract) seat allocator.  The
 * amortized cost (K calls running in O(K) time, but not O(1) each) is
 * due to attempting to resolve collisions on hold IDs (see code), and
 * is avoidable only if multiple holds per customer are disallowed
 * (then it would suffice to index holds by email instead of hold ID).
 *
 * Algorithm overview
 * ==================

 * - maintain an First-In-First-Out map (LinkedHashMap) that maps each
 *   hold ID to a SeatHold instance
 *     - O(1)-time insertion when a hold is created,
 *     - O(1)-time deletion when a hold is reserved or expired
 * - store expiration time and customer's email in SeatHold
 *   (these would anyway be needed for displaying a hold to the customer)
 * - lazily expire holds in amortized O(1) time at the front of the FIFO map
 *   (note that expiration times are non-decreasing due to FIFO order)
 *   at the beginning of any of the following method calls:
 *     - findAndHold() - to avoid _missing empty seats_ (false negatives)
 *     - reserveSeats() - to avoid reserving _seats that expired_
 *     - numSeatsAvailable() - for _up-to-date_ information
 */
abstract class BaseTicketService implements TicketService {

  /**
   * For security purposes, the implementation does not reveal the
   * reason why it fails, which could be any of the following:
   * - there is no hold associated with the customer email
   * - the hold associated with the customer email has expired
   * - the provided Id does not match the one in the existing hold
   *
   * The returned confirmation code is in format XXXXXXXX-CC, e.g.,
   *   3501B1B1-9E (X and C are hexadecimal digits)
   * and is guaranteed to be traceable to the originating hold ID
   * without extra bookkeeping (i.e., it is a perfect/two-way hash).
   *
   * Time complexity: O(1) amortized (may trigger expiration)
   *                    +
   *                  O(allocator.release(N))
   *
   * @param seatHoldId the matching reservation
   * @return confirmation code or null if reservation failed,
   */
  @Override
  public String reserveSeats(int seatHoldId, String customerEmail) {
    Objects.requireNonNull(customerEmail);
    synchronized (mutex) {
      expireHolds();
      SeatHold hold = holds.get(seatHoldId);
      if (hold == null || customerEmail != hold.customerEmail)
        return null;

      holds.remove(seatHoldId);  // remove from expiration queue
    }

    // Derive the confirmation code from the secure hash (ID)
    // but add some redundancy in a form of a XOR checksum,
    // so the ticket agent can better assist customers who.
    // Use a two-way hash to ensure that _no collision_ occurs,
    // since collisions of a secure hash (ID) are handled earlier.
    final int code = seatHoldId ^ 0xCAFEBABE;  // two-way hash
    final int byteMask = 0xff;
    int checkSum = (code & byteMask) ^ ((code>>4) & byteMask) ^
        ((code>>16) & byteMask) ^ ((code>>24) & byteMask);
    return String.format("%08X-%02X", code, checkSum);
  }

  /**
   * This implementation permits multiple holds for the same customer,
   * with the same semantics as Amazon's Lightning Deals; i.e., the
   * same customer can find and hold additional seats independently,
   * and/or let the former seat group expire by not reserving them
   * (new holds do _not_ extend the expiration time of previous ones).
   *
   * Time complexity: O(1) amortized [O(K) with probability (2^-32)^K]
   *                    +
   *                  O(allocator.find(N) + allocator.release(N))
   *
   * @param numSeats the number of seats to find and hold
   * @param customerEmail unique identifier for the customer
   * @return a SeatHold object identifying the specific seats
   *         (use getId() to retrieve the identifier)
   */
  @Override
  public SeatHold findAndHoldSeats(int numSeats, String customerEmail) {
    java.util.Objects.requireNonNull(customerEmail);
    if (numSeats > maxAvailableCount || numSeats <= 0)
      throw new IllegalArgumentException(
          "This stage can accommodate only up to " + maxAvailableCount + " people");

    synchronized (mutex) {
      expireHolds();
      if (numSeats > availableCount)
        return null;

      // Derive a cryptographically secure ID from time and email,
      // finely increasing the time in case of a collision.  Use salt
      // as a defense against attackers who can guess email & time.
      int id;
      long relTime = clock.millis();
      do {
        id = generateCryptoSecureId(customerEmail, relTime++, "SALT");
      } while (holds.containsKey(id));
      log.info(String.format("ID(%s,%s) = %08X", customerEmail, clock.instant(), id));

      SeatHold hold = new SeatHold(id);
      holds.put(id, hold);
      allocator.allocate(numSeats, hold);
      availableCount -= numSeats;
      hold.customerEmail = customerEmail;
      hold.expirationTime = clock.instant().plus(holdExpiration);
      log.info(
          String.format("Held %2d seats; now available: %3d", numSeats, availableCount) +
          "    | " + hold);
      return hold;
    }
  }

  static final int generateCryptoSecureId(String customerEmail, long relTime, String salt) {
    try {
      byte[] bytes = java.security.MessageDigest.getInstance("SHA1").digest(
          (customerEmail + salt + relTime).getBytes());
      assert bytes.length % 4 == 0 : "sanity check: SHA1 size is divisible by 4";
      int ret = 0;                              // int is 4 bytes in Java, so
      for (int i = 0; i < bytes.length; i += 4) { // XOR each groups of 4 bytes
        ret <<= 4;
        ret ^= bytes[i] | bytes[i + 1] | bytes[i + 2] | bytes[i + 3];
      }
      return ret;
    } catch (java.security.NoSuchAlgorithmException ignore) {
      throw new RuntimeException("BUG: trying to use non-existing SHA1 algorithm");
    }
  }

  @Override
  public int numSeatsAvailable() {
    synchronized (mutex) {
      expireHolds();
      return availableCount;
    }
  }

  // exposed stuff within a package, so it can be mocked in unit tests
  Clock clock = Clock.systemDefaultZone();
  java.util.function.Consumer<SeatHold> expiredHoldVisitor = (x) -> {}; // no-op

  protected BaseTicketService(SeatLayout layout, Duration holdExpiration) {
    Objects.requireNonNull(holdExpiration);
    Objects.requireNonNull(layout);
    this.maxAvailableCount = this.availableCount =
        layout.getRowCount() * layout.getSeatsPerRowCount();
    this.allocator = createAllocator(layout);
    this.holdExpiration = holdExpiration;
    log.info("    max available: " + maxAvailableCount + " = " +
             layout.getRowCount() + "*" + layout.getSeatsPerRowCount());
  }

  protected abstract Allocator createAllocator(SeatLayout layout);

  /**
   * Allocator encapsulates the strategy for finding and releasing
   * seats/tickets, and uses a particular algorithm for finding the
   * best available seats.
   *
   * It is not thread-safe, so the caller should prevent data races.
   */
  protected static abstract class Allocator {
    /**
     * Allocates the requested number of seats, adding them to a hold.
     *
     * @param numSeats the requested number of seats (must be available)
     * @param hold a hold to store the information on allocated seats
     * @return true unless the allocator for some reason refuses to
     *         honor the request (e.g., too many bad seats?)
     */
    public abstract boolean allocate(int numSeats, SeatHold hold);

    /**
     * Releases the seats in a hold so that they can to be allocated
     * under the assumption that they were allocated before the call.
     *
     * @param hold a hold that stores the information on allocated seats
     */
    public abstract void release(SeatHold hold);

    public Allocator(SeatLayout layout) {
      this.layout = layout;
    }
    protected final SeatLayout layout;
  }

  private final Allocator allocator;

  // Removes all expired holds from the queue and returns their count
  private int expireHolds() {
    int expiredCount = 0;
    Iterator<Map.Entry<Integer, SeatHold>> it = holds.entrySet().iterator();
    java.time.Instant now = clock.instant();
    SeatHold hold;
    while (it.hasNext() &&
           (hold = it.next().getValue()).expirationTime.compareTo(now) <= 0) {
      it.remove();
      allocator.release(hold);
      log.info("expiredHoldVisitor(" + hold + ") @ " + hold.expirationTime + " < " + now);
      expiredHoldVisitor.accept(hold);
      expiredCount += hold.seatCount();
    }
    availableCount += expiredCount;
    if (expiredCount != 0)
      log.info("Expired " + expiredCount + " seats; now available: " + availableCount);
    return expiredCount;
  }

  // A map that maintains holds in their increasing order of expiration
  private LinkedHashMap<Integer, SeatHold> holds = new LinkedHashMap<>();

  // the number of currently and initially available seats, respectively
  private int availableCount, maxAvailableCount;

  private final Duration holdExpiration;

  // The object to synchronize on during atomic transactions in
  // (findAndHold|reserve)Seats.
  private final Object mutex = new Object();

  private static Logger log = Logger.getLogger("BaseTicketService");
}
