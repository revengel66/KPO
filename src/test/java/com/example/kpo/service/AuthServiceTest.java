package com.example.kpo.service;

import com.example.kpo.dto.AuthResponse;
import com.example.kpo.dto.LoginRequest;
import com.example.kpo.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Mock
    private Authentication authentication;

    @Test
    @DisplayName("login делегирует AuthenticationManager и возвращает токен")
    void loginReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("demo");
        request.setPassword("secret");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getName()).thenReturn("demo");
        when(jwtService.generateToken("demo")).thenReturn("token123");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("token123");
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        UsernamePasswordAuthenticationToken token = captor.getValue();
        assertThat(token.getPrincipal()).isEqualTo("demo");
        assertThat(token.getCredentials()).isEqualTo("secret");
        verify(jwtService).generateToken("demo");
    }

    @Test
    @DisplayName("login пробрасывает ошибку если аутентификация не удалась")
    void loginPropagatesAuthenticationFailure() {
        LoginRequest request = new LoginRequest();
        request.setUsername("demo");
        request.setPassword("wrong");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("invalid"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
