package io.github.posseidon.knowledgebase.it.interview.web;

import io.github.posseidon.knowledgebase.it.interview.basket.Basket;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final Basket basket;

    public GlobalModelAttributes(Basket basket) {
        this.basket = basket;
    }

    @ModelAttribute("basketSize")
    public int basketSize() {
        return basket.size();
    }
}
