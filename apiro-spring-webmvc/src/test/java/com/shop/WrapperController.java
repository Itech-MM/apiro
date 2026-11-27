package com.shop;

import com.shop.dto.User;
import org.springframework.web.bind.annotation.RequestBody;

public class WrapperController {
    public void order(@RequestBody ApiResponse<Order> request) {
    }

    public void user(@RequestBody ApiResponse<User> request) {
    }
}
