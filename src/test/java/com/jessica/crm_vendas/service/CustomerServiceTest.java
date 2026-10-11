package com.jessica.crm_vendas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jessica.crm_vendas.dto.CustomerRequestDTO;
import com.jessica.crm_vendas.dto.CustomerResponseDTO;
import com.jessica.crm_vendas.exception.ConflictException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    @InjectMocks
    private CustomerService service;

    @Test
    void createUsesLeadStatusAndNormalizesEmail() {
        when(repository.existsByEmail("ana@empresa.com")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponseDTO created = service.create(
                new CustomerRequestDTO("  Ana Souza ", " Ana@Empresa.com ", "", "Empresa X", null));

        assertThat(created.name()).isEqualTo("Ana Souza");
        assertThat(created.email()).isEqualTo("ana@empresa.com");
        assertThat(created.phone()).isNull();
        assertThat(created.status()).isEqualTo(CustomerStatus.LEAD);
    }

    @Test
    void createRejectsDuplicatedEmail() {
        when(repository.existsByEmail("ana@empresa.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CustomerRequestDTO("Ana", "ana@empresa.com", null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAllowsKeepingOwnEmail() {
        Customer existing = new Customer();
        existing.setId(7L);
        existing.setName("Ana");
        existing.setEmail("ana@empresa.com");
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.existsByEmailAndIdNot("ana@empresa.com", 7L)).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        CustomerResponseDTO updated = service.update(7L,
                new CustomerRequestDTO("Ana Souza", "ana@empresa.com", null, null, CustomerStatus.CUSTOMER));

        assertThat(updated.name()).isEqualTo("Ana Souza");
        assertThat(updated.status()).isEqualTo(CustomerStatus.CUSTOMER);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
