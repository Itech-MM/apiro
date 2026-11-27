package com.shop;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class OrderLine {
    @NotBlank
    @Size(max = 40)
    public String sku;

    @NotNull
    @Min(1)
    public int quantity;
}
