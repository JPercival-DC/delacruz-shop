package edu.cit.delacruz.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface FeedCursorRepository extends JpaRepository<FeedCursor, Integer> {
}
