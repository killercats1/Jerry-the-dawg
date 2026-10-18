package com.jerrythedawg.voice;

/** Where the listeners send what they understood. */
interface Heard {
    void heard(String text);
}
