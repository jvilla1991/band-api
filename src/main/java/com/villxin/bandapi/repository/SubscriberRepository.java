package com.villxin.bandapi.repository;

import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.entity.Subscriber.MailchimpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    boolean existsByEmail(String email);

    List<Subscriber> findByMailchimpStatusInOrderByIdAsc(Collection<MailchimpStatus> statuses);

    long countByMailchimpStatus(MailchimpStatus status);
}
