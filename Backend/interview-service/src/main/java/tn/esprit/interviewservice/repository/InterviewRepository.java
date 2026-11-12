package tn.esprit.interviewservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.interviewservice.entity.Interview;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationId(Long applicationId);

    List<Interview> findByStudentId(String studentId);

    List<Interview> findByCompanyId(Long companyId);
}
