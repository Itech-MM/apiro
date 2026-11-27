package com.shop;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

public class CreateOrder {
    @NotEmpty
    public List<OrderLine> items;

    @Size(max = 200)
    public String note;
}
