package com.wayt.controller;

import com.wayt.dto.HistoryDtos;
import com.wayt.service.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    List<HistoryDtos.HistoryItemResponse> list() {
        return historyService.list();
    }

    @GetMapping("/{appointmentId}")
    HistoryDtos.HistoryItemResponse get(@PathVariable UUID appointmentId) {
        return historyService.get(appointmentId);
    }
}
