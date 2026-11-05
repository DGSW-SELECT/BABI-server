package com.select.domain.vaccination.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vaccines")
@Getter
@NoArgsConstructor
public class Vaccine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vaccine_name")
    private String name;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "recommended_start_month")
    private Integer recommendedStartMonth;

    @Column(name = "recommended_end_month")
    private Integer recommendedEndMonth;
}
