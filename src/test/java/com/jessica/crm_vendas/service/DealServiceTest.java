package com.jessica.crm_vendas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.jessica.crm_vendas.dto.DealRequestDTO;
import com.jessica.crm_vendas.dto.DealResponseDTO;
import com.jessica.crm_vendas.dto.StageChangeRequestDTO;
import com.jessica.crm_vendas.event.DealStageChangedEvent;
import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.DealStageChange;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealStageChangeRepository;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

    @Mock private DealRepository repository;
    @Mock private CustomerRepository customerRepository;
    @Mock private DealStageChangeRepository stageChangeRepository;
    @Mock private ApplicationEventPublisher events;

    private DealService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new DealService(repository, customerRepository, stageChangeRepository, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        customer = new Customer();
        customer.setId(1L);
        customer.setName("Carla Mendes");
    }

    private Deal existingDeal(DealStage stage) {
        Deal deal = new Deal();
        deal.setId(10L);
        deal.setTitle("App de pedidos");
        deal.setValue(new BigDecimal("38000"));
        deal.setCustomer(customer);
        deal.setStage(stage);
        deal.setStageChangedAt(NOW.minusSeconds(86_400 * 5));
        return deal;
    }

    @Test
    void createStartsAsProspectAndRecordsHistory() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> {
            Deal deal = invocation.getArgument(0);
            deal.setId(10L);
            return deal;
        });

        DealResponseDTO created = service.create(new DealRequestDTO(
                " App de pedidos ", 1L, new BigDecimal("38000"), null, null, null, null));

        assertThat(created.title()).isEqualTo("App de pedidos");
        assertThat(created.stage()).isEqualTo(DealStage.PROSPECT);
        assertThat(created.stageChangedAt()).isEqualTo(NOW);
        assertThat(created.closedAt()).isNull();

        ArgumentCaptor<DealStageChange> change = ArgumentCaptor.forClass(DealStageChange.class);
        verify(stageChangeRepository).save(change.capture());
        assertThat(change.getValue().getFromStage()).isNull();
        assertThat(change.getValue().getToStage()).isEqualTo(DealStage.PROSPECT);
    }

    @Test
    void movingToWonClosesTheDealAndPublishesEvent() {
        Deal deal = existingDeal(DealStage.PROPOSAL);
        when(repository.findById(10L)).thenReturn(Optional.of(deal));
        when(repository.saveAndFlush(deal)).thenReturn(deal);

        DealResponseDTO moved = service.changeStage(10L, new StageChangeRequestDTO(DealStage.WON, null));

        assertThat(moved.stage()).isEqualTo(DealStage.WON);
        assertThat(moved.closedAt()).isEqualTo(NOW);
        assertThat(moved.stageChangedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new DealStageChangedEvent(10L, DealStage.PROPOSAL, DealStage.WON, NOW));
    }

    @Test
    void movingToLostRequiresReason() {
        Deal deal = existingDeal(DealStage.PROPOSAL);
        when(repository.findById(10L)).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> service.changeStage(10L, new StageChangeRequestDTO(DealStage.LOST, "  ")))
                .isInstanceOf(BusinessRuleException.class);
        verify(stageChangeRepository, never()).save(any());
        assertThat(deal.getStage()).isEqualTo(DealStage.PROPOSAL);
    }

    @Test
    void reopeningALostDealClearsReasonAndCloseDate() {
        Deal deal = existingDeal(DealStage.LOST);
        deal.setLostReason("Preço");
        deal.setClosedAt(NOW.minusSeconds(3600));
        when(repository.findById(10L)).thenReturn(Optional.of(deal));
        when(repository.saveAndFlush(deal)).thenReturn(deal);

        DealResponseDTO moved = service.changeStage(10L, new StageChangeRequestDTO(DealStage.QUALIFIED, null));

        assertThat(moved.lostReason()).isNull();
        assertThat(moved.closedAt()).isNull();
    }

    @Test
    void droppingOnTheSameStageChangesNothing() {
        Deal deal = existingDeal(DealStage.QUALIFIED);
        Instant before = deal.getStageChangedAt();
        when(repository.findById(10L)).thenReturn(Optional.of(deal));
        when(repository.saveAndFlush(deal)).thenReturn(deal);

        service.changeStage(10L, new StageChangeRequestDTO(DealStage.QUALIFIED, null));

        assertThat(deal.getStageChangedAt()).isEqualTo(before);
        verify(stageChangeRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
