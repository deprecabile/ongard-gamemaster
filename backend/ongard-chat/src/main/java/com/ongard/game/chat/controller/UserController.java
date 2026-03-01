package com.ongard.game.chat.controller;

import com.ongard.game.chat.contract.UserCreateRequest;
import com.ongard.game.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping( "/api/user" )
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<Void> createUser(@RequestBody UserCreateRequest request) {
    userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
