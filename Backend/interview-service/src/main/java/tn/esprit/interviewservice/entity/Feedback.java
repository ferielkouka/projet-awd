package tn.esprit.interviewservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Min(1)
    @Max(5)
    private Integer note;

    @Column(length = 1000)
    private String commentaire;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    private LocalDateTime dateCreation;

    @OneToOne
    @JoinColumn(name = "interview_id", nullable = false, unique = true)
    private Interview interview;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }
}
