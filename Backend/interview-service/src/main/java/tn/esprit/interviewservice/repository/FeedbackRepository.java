package tn.esprit.interviewservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.interviewservice.entity.Feedback;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByInterviewId(Long interviewId);

    boolean existsByInterviewId(Long interviewId);
}
