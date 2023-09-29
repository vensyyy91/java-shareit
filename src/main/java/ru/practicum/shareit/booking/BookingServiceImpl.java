package ru.practicum.shareit.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.booking.dto.BookingCreationDto;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.BookingMapper;
import ru.practicum.shareit.exception.*;
import ru.practicum.shareit.item.Item;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    @Override
    public BookingDto addBooking(long userId, BookingCreationDto bookingCreationDto) {
        User booker = checkUser(userId);
        Item item = checkItem(bookingCreationDto.getItemId());
        if (item.getOwner() == booker.getId()) {
            throw new AccessDeniedException("Владелец не может забронировать собственную вещь.");
        }
        if (!item.getAvailable()) {
            throw new ItemUnavailableException("Вещь с id=" + bookingCreationDto.getItemId() + " недоступна.");
        }
        Booking booking = BookingMapper.toBooking(bookingCreationDto);
        booking.setItem(item);
        booking.setBooker(booker);
        booking.setStatus(Status.WAITING);
        Booking newBooking = bookingRepository.save(booking);
        log.info(String.format("Пользователем с id=%d добавлено бронирование: %s", userId, newBooking));

        return BookingMapper.toBookingDto(newBooking);
    }

    @Override
    public BookingDto approveBooking(long userId, long bookingId, boolean approved) {
        Booking booking = checkBooking(bookingId);
        User owner = checkUser(userId);
        if (booking.getItem().getOwner() != owner.getId()) {
            throw new AccessDeniedException("Подтверждать или отклонять бронирование может только владелец вещи.");
        }
        if (booking.getStatus() != Status.WAITING) {
            throw new BookingUnavailableException("Данное бронирование уже было подтверждено или отклонено ранее.");
        }
        booking.setStatus(approved ? Status.APPROVED : Status.REJECTED);
        bookingRepository.save(booking);
        log.info(String.format("Владелец вещи изменил статус бронирования с id=%d на %s", bookingId, booking.getStatus()));

        return BookingMapper.toBookingDto(booking);
    }

    @Override
    public BookingDto getBooking(long userId, long bookingId) {
        Booking booking = checkBooking(bookingId);
        User user = checkUser(userId);
        if (booking.getItem().getOwner() != user.getId() && booking.getBooker().getId() != user.getId()) {
            throw new AccessDeniedException("Получить информацию о бронировании может только владелец вещи или автор бронирования.");
        }
        log.info("Возвращено бронирование: " + booking);

        return BookingMapper.toBookingDto(booking);
    }

    @Override
    public List<BookingDto> getUserBookings(long userId, State state) {
        checkUser(userId);
        List<Booking> bookings = new ArrayList<>();
        switch (state) {
            case ALL:
                bookings = bookingRepository.findAllByBookerIdOrderByStartDesc(userId);
                break;
            case PAST:
                bookings = bookingRepository.findAllByBookerIdAndEndBeforeOrderByStartDesc(userId, LocalDateTime.now());
                break;
            case CURRENT:
                bookings = bookingRepository.findAllCurrentByBookerId(userId, LocalDateTime.now());
                break;
            case FUTURE:
                bookings = bookingRepository.findAllByBookerIdAndStartAfterOrderByStartDesc(userId, LocalDateTime.now());
                break;
            case WAITING:
                bookings = bookingRepository.findAllByBookerIdAndStatusOrderByStartDesc(userId, Status.WAITING);
                break;
            case REJECTED:
                bookings = bookingRepository.findAllByBookerIdAndStatusOrderByStartDesc(userId, Status.REJECTED);
                break;
        }
        log.info(String.format("Возвращен список всех бронирований пользователя с id=%d, параметр state=%s: %s",
                userId, state, bookings));

        return bookings.stream().map(BookingMapper::toBookingDto).collect(Collectors.toList());
    }

    @Override
    public List<BookingDto> getUserItemsBookings(long userId, State state) {
        checkUser(userId);
        List<Booking> bookings = new ArrayList<>();
        switch (state) {
            case ALL:
                bookings = bookingRepository.findAllByItemOwnerOrderByStartDesc(userId);
                break;
            case PAST:
                bookings = bookingRepository.findAllByItemOwnerAndEndBeforeOrderByStartDesc(userId, LocalDateTime.now());
                break;
            case CURRENT:
                bookings = bookingRepository.findAllCurrentByItemOwner(userId, LocalDateTime.now());
                break;
            case FUTURE:
                bookings = bookingRepository.findAllByItemOwnerAndStartAfterOrderByStartDesc(userId, LocalDateTime.now());
                break;
            case WAITING:
                bookings = bookingRepository.findAllByItemOwnerAndStatusOrderByStartDesc(userId, Status.WAITING);
                break;
            case REJECTED:
                bookings = bookingRepository.findAllByItemOwnerAndStatusOrderByStartDesc(userId, Status.REJECTED);
                break;
        }
        log.info(String.format("Возвращен список бронирований для всех вещей пользователя с id=%d, параметр state=%s: %s",
                userId, state, bookings));

        return bookings.stream().map(BookingMapper::toBookingDto).collect(Collectors.toList());
    }

    private User checkUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь с id= " + userId + " не найден."));
    }

    private Item checkItem(long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Вещь с id=" + itemId + " не найдена."));
    }

    private Booking checkBooking(long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Бронирование с id= " + bookingId + " не найдено."));
    }
}