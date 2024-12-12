package com;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	@GetMapping("/subscribe")
	public SseEmitter subscribe() {

		SseEmitter emitter = new SseEmitter(0L);
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError((ex) -> {
			emitters.remove(emitter);
			System.err.println("Client disconnected: " + ex.getMessage());
		});

		try {
			emitter.send(SseEmitter.event().name("connection").data("Connected"));
		} catch (IOException e) {
			emitters.remove(emitter);
		}
		return emitter;
	}

	@PostMapping("/update")
	public String sendInventoryUpdate(@RequestBody Map<String, String> update) {
		String product = update.get("product");
		String status = update.get("status");
		List<SseEmitter> deadEmitters = new ArrayList<>();
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(
						SseEmitter.event().name("inventory-update").data("Product: " + product + " is now " + status));
			} catch (IOException e) {
				deadEmitters.add(emitter);
			}
		}
		emitters.removeAll(deadEmitters);
		return "Update sent to " + emitters.size() + " subscribers.";
	}

	public InventoryController() {
		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
			List<SseEmitter> deadEmitters = new ArrayList<>();
			for (SseEmitter emitter : emitters) {
				try {
					emitter.send(SseEmitter.event().name("keep-alive").data("Ping"));
				} catch (IOException e) {
					deadEmitters.add(emitter);
				}
			}
			emitters.removeAll(deadEmitters);
		}, 0, 15, TimeUnit.SECONDS);
	}
}
