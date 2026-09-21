package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.CreateRoomRequest;
import com.resortapi.resortbooking.dto.CreateRoomTypeRequest;
import com.resortapi.resortbooking.dto.RoomForm;
import com.resortapi.resortbooking.dto.RoomTypeDto;
import com.resortapi.resortbooking.dto.RoomTypeForm;
import com.resortapi.resortbooking.entity.RoomStatus;
import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.service.RoomService;
import com.resortapi.resortbooking.service.RoomTypeService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Admin UI over rooms and room types - both already fully served by the REST API. */
@Controller
@RequestMapping("/admin")
public class AdminCatalogPageController {

    private final RoomTypeService roomTypeService;
    private final RoomService roomService;

    public AdminCatalogPageController(RoomTypeService roomTypeService, RoomService roomService) {
        this.roomTypeService = roomTypeService;
        this.roomService = roomService;
    }

    @GetMapping("/room-types")
    public String roomTypes(Model model) {
        model.addAttribute("roomTypes", roomTypeService.findAll());
        if (!model.containsAttribute("roomTypeForm")) {
            model.addAttribute("roomTypeForm", new RoomTypeForm());
        }
        return "admin/room-types";
    }

    @PostMapping("/room-types")
    public String createRoomType(@Valid @ModelAttribute("roomTypeForm") RoomTypeForm form,
                                 BindingResult result, Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                roomTypeService.create(new CreateRoomTypeRequest(
                        form.getName(), form.getCapacity(), form.getBasePrice(), form.getDescription()));
                flash.addFlashAttribute("success", "Room type \"" + form.getName() + "\" added.");
                return "redirect:/admin/room-types";
            } catch (DuplicateResourceException e) {
                result.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("roomTypes", roomTypeService.findAll());
        return "admin/room-types";
    }

    @GetMapping("/room-types/{id}/edit")
    public String editRoomTypeForm(@PathVariable Long id, Model model, RedirectAttributes flash) {
        RoomTypeDto roomType;
        try {
            roomType = roomTypeService.findById(id);
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "That room type could not be found.");
            return "redirect:/admin/room-types";
        }
        if (!model.containsAttribute("roomTypeForm")) {
            RoomTypeForm form = new RoomTypeForm();
            form.setName(roomType.name());
            form.setCapacity(roomType.capacity());
            form.setBasePrice(new BigDecimal(roomType.basePrice()));
            form.setDescription(roomType.description());
            model.addAttribute("roomTypeForm", form);
        }
        model.addAttribute("roomTypeId", id);
        return "admin/room-type-edit";
    }

    @PostMapping("/room-types/{id}")
    public String updateRoomType(@PathVariable Long id,
                                 @Valid @ModelAttribute("roomTypeForm") RoomTypeForm form,
                                 BindingResult result, Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                roomTypeService.update(id, new CreateRoomTypeRequest(
                        form.getName(), form.getCapacity(), form.getBasePrice(), form.getDescription()));
                flash.addFlashAttribute("success", "Room type updated.");
                return "redirect:/admin/room-types";
            } catch (DuplicateResourceException e) {
                result.rejectValue("name", "duplicate", e.getMessage());
            } catch (ResourceNotFoundException e) {
                flash.addFlashAttribute("error", "That room type could not be found.");
                return "redirect:/admin/room-types";
            }
        }
        model.addAttribute("roomTypeId", id);
        return "admin/room-type-edit";
    }

    @GetMapping("/rooms")
    public String rooms(Model model) {
        model.addAttribute("rooms", roomService.findAll());
        model.addAttribute("roomTypes", roomTypeService.findAll());
        if (!model.containsAttribute("roomForm")) {
            model.addAttribute("roomForm", new RoomForm());
        }
        return "admin/rooms";
    }

    @PostMapping("/rooms")
    public String createRoom(@Valid @ModelAttribute("roomForm") RoomForm form,
                             BindingResult result, Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                roomService.create(new CreateRoomRequest(form.getRoomNumber(), form.getRoomTypeId()));
                flash.addFlashAttribute("success", "Room " + form.getRoomNumber() + " added.");
                return "redirect:/admin/rooms";
            } catch (DuplicateResourceException e) {
                result.rejectValue("roomNumber", "duplicate", e.getMessage());
            } catch (ResourceNotFoundException e) {
                result.rejectValue("roomTypeId", "notfound", "That room type could not be found.");
            }
        }
        model.addAttribute("rooms", roomService.findAll());
        model.addAttribute("roomTypes", roomTypeService.findAll());
        return "admin/rooms";
    }

    /** FR-14 / BR-10: send a room for maintenance, or return it to service. */
    @PostMapping("/rooms/{id}/{status}")
    public String updateRoomStatus(@PathVariable Long id, @PathVariable String status, RedirectAttributes flash) {
        RoomStatus target = switch (status) {
            case "available" -> RoomStatus.AVAILABLE;
            case "maintenance" -> RoomStatus.MAINTENANCE;
            default -> null;
        };
        if (target == null) {
            flash.addFlashAttribute("error", "Unknown room status.");
            return "redirect:/admin/rooms";
        }
        try {
            var room = roomService.updateStatus(id, target);
            flash.addFlashAttribute("success", "Room " + room.roomNumber() + " is now " + status + ".");
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "That room could not be found.");
        }
        return "redirect:/admin/rooms";
    }
}
