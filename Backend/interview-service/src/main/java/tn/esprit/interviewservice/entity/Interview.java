package tn.esprit.interviewservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Références vers d'autres microservices : simples identifiants, pas de relation JPA
    @Column(nullable = false)
    private Long applicationId;   // application-service

    private String studentId;     // id Keycloak (UUID)

    private Long companyId;       // company-service

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewType type;

    private String lieu;          // si PRESENTIEL

    private String lienVisio;     // si EN_LIGNE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus statut;

    private LocalDateTime dateCreation;

    @OneToOne(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    private Feedback feedback;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) {
            statut = InterviewStatus.PLANIFIE;
        }
    }
}
