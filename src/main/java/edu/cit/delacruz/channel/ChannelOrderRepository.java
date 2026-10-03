package edu.cit.delacruz.channel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, Long> {

    Optional<ChannelOrder> findByTiangeOrderId(String tiangeOrderId);

    Optional<ChannelOrder> findByShopOrderId(Long shopOrderId);
}
