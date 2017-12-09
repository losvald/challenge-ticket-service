## Currently implemented components:

- `TicketService` - defines public interface and entities (`SeatHold`, etc.)
- `BaseTicketService` - implements ticket service with a pluggable seat allocation strategy
- 2 variants of the `BaseTicketService`, both implementing a greedy strategy
    - `ThreePassGreedyTicketService` - has **optimal space** efficiency & fast for small stages
    - `SublinearGreedyTicketService` - has **optimal time** complexity / fast for big stages

## Testing strategy

- system clock is mocked by injecting a fixed clock through package-private access
- the **core functionality** of a ticket service (see code comments for
details) is tested *independently* from the **seat allocation** (i.e.,
which exact seats are assigned) in `BaseTicketServiceTest`
- seat allocation algorithm is tested as:
    - *black* box (in conjunction with the ticket service) in `ThreePassGreedyTicketServiceTest`
    - *white* box (by directly calling `Allocator`'s methods) in `SublinearGreedyTicketService`
- `TicketServiceTest` tests the `SeatHold` entities and their public interface
