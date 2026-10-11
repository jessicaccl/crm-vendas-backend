package com.jessica.crm_vendas.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/** Popula o banco com clientes fictícios quando ele está vazio (DEMO_DATA=true). */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final CustomerRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        List<Customer> customers = List.of(
            customer("Carla Mendes", "compras@bompreco.com.br", "(11) 98877-1020", "Rede Mercadinho Bom Preço", CustomerStatus.LEAD),
            customer("Bruno Tavares", "bruno@amigofiel.com.br", "(21) 99654-3311", "Pet Shop Amigo Fiel", CustomerStatus.CUSTOMER),
            customer("Helena Prado", "contato@sorrisoleve.com.br", "(31) 98123-4455", "Clínica Sorriso Leve", CustomerStatus.LEAD),
            customer("Marcos Lima", "marcos@corpoemacao.com.br", "(11) 97766-2201", "Academia Corpo em Ação", CustomerStatus.LEAD),
            customer("Rita Nogueira", "rita@paodourado.com.br", "(41) 99812-7788", "Padaria Pão Dourado", CustomerStatus.LEAD),
            customer("Paulo Siqueira", "paulo@nortesul.com.br", "(85) 98544-1290", "Distribuidora Norte Sul", CustomerStatus.LEAD),
            customer("Lúcia Andrade", "lucia@pequenosaber.com.br", "(51) 99231-0098", "Escola Pequeno Saber", CustomerStatus.CUSTOMER),
            customer("Fernanda Rocha", "fernanda@larcerto.com.br", "(11) 96612-4410", "Imobiliária Lar Certo", CustomerStatus.INACTIVE)
        );

        repository.saveAll(customers);
        log.info("Dados de demonstração criados: {} clientes", customers.size());
    }

    private static Customer customer(String name, String email, String phone, String company, CustomerStatus status) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setCompany(company);
        customer.setStatus(status);
        return customer;
    }
}
