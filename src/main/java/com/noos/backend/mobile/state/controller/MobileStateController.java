package com.noos.backend.mobile.state.controller;

import com.noos.backend.mobile.state.dto.MeasureRequest;
import com.noos.backend.mobile.state.dto.MeasureResponse;
import com.noos.backend.mobile.state.service.StateMeasurementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/state")
public class MobileStateController {

    private final StateMeasurementService stateMeasurementService;

    public MobileStateController(StateMeasurementService stateMeasurementService) {
        this.stateMeasurementService = stateMeasurementService;
    }

    @PostMapping("/measure")
    public MeasureResponse measure(@RequestHeader("x-device-id") String deviceId,
                                   @Valid @RequestBody MeasureRequest request) {
        return stateMeasurementService.measure(request, deviceId);
    }
}
