package com.select.domain.voice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "voice_inputs")
public class VoiceInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
