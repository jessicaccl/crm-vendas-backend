package com.jessica.crm_vendas.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.DealStageChange;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealStageChangeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Popula um banco vazio com clientes e negócios fictícios (DEMO_DATA=true),
 * com datas relativas a hoje para os indicadores fazerem sentido em qualquer dia.
 * Não faz nada se já existir algum cliente, então nunca mistura exemplos com dados reais.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final DealStageChangeRepository stageChangeRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            return;
        }

        Customer carla = customer("Carla Mendes", "compras@bompreco.com.br", "(11) 98877-1020", "Rede Mercadinho Bom Preço", CustomerStatus.LEAD);
        Customer bruno = customer("Bruno Tavares", "bruno@amigofiel.com.br", "(21) 99654-3311", "Pet Shop Amigo Fiel", CustomerStatus.CUSTOMER);
        Customer helena = customer("Helena Prado", "contato@sorrisoleve.com.br", "(31) 98123-4455", "Clínica Sorriso Leve", CustomerStatus.LEAD);
        Customer marcos = customer("Marcos Lima", "marcos@corpoemacao.com.br", "(11) 97766-2201", "Academia Corpo em Ação", CustomerStatus.CUSTOMER);
        Customer rita = customer("Rita Nogueira", "rita@paodourado.com.br", "(41) 99812-7788", "Padaria Pão Dourado", CustomerStatus.LEAD);
        Customer paulo = customer("Paulo Siqueira", "paulo@nortesul.com.br", "(85) 98544-1290", "Distribuidora Norte Sul", CustomerStatus.LEAD);
        Customer lucia = customer("Lúcia Andrade", "lucia@pequenosaber.com.br", "(51) 99231-0098", "Escola Pequeno Saber", CustomerStatus.CUSTOMER);
        Customer fernanda = customer("Fernanda Rocha", "fernanda@larcerto.com.br", "(11) 96612-4410", "Imobiliária Lar Certo", CustomerStatus.INACTIVE);
        customerRepository.saveAll(List.of(carla, bruno, helena, marcos, rita, paulo, lucia, fernanda));

        Instant now = clock.instant();
        ZonedDateTime today = ZonedDateTime.now(clock);
        // Datas que sempre caem no mês atual e no mês anterior, seja qual for o dia de hoje
        Instant monthStart = today.toLocalDate().withDayOfMonth(1).atStartOfDay(today.getZone()).toInstant();
        Instant thisMonthEarly = later(monthStart, earlier(monthStart.plus(Duration.ofHours(10)), now.minus(Duration.ofHours(1))));
        Instant thisMonthRecent = later(thisMonthEarly, now.minus(Duration.ofMinutes(30)));
        Instant lastMonth = today.withDayOfMonth(1).minusMonths(1).plusDays(12).toInstant();
        Instant lastMonthLate = today.withDayOfMonth(1).minusDays(3).toInstant();
        LocalDate date = today.toLocalDate();

        List<Deal> deals = List.of(
            deal("Site institucional", rita, "4800", DealStage.PROSPECT, DealSource.INSTAGRAM, daysAgo(now, 2), date.plusDays(30), null),
            deal("Plano anual de manutenção", helena, "12000", DealStage.PROSPECT, DealSource.REFERRAL, daysAgo(now, 9), date.plusDays(45), null),
            deal("Cardápio digital", rita, "2600", DealStage.PROSPECT, DealSource.WHATSAPP, daysAgo(now, 1), null, null),
            deal("Sistema de agendamento", marcos, "9600", DealStage.QUALIFIED, DealSource.WEBSITE, daysAgo(now, 4), date.plusDays(20), null),
            deal("Programa de fidelidade", bruno, "7400", DealStage.QUALIFIED, DealSource.REFERRAL, daysAgo(now, 8), date.plusDays(25), null),
            deal("App de pedidos", carla, "38000", DealStage.PROPOSAL, DealSource.REFERRAL, daysAgo(now, 3), date.plusDays(19), null),
            deal("Integração com ERP", paulo, "22500", DealStage.PROPOSAL, DealSource.WEBSITE, daysAgo(now, 10), date.plusDays(1), null),
            deal("Portal do aluno", lucia, "15000", DealStage.PROPOSAL, DealSource.REFERRAL, daysAgo(now, 5), date.plusDays(12), null),
            deal("E-commerce completo", bruno, "27300", DealStage.WON, DealSource.REFERRAL, thisMonthRecent, null, null),
            deal("Landing de campanha", lucia, "3500", DealStage.WON, DealSource.INSTAGRAM, thisMonthEarly, null, null),
            deal("Identidade visual", marcos, "6200", DealStage.WON, DealSource.INSTAGRAM, lastMonth, null, null),
            deal("Site da escola", lucia, "18000", DealStage.WON, DealSource.WEBSITE, lastMonthLate, null, null),
            deal("Chatbot de atendimento", fernanda, "8900", DealStage.LOST, DealSource.WHATSAPP, daysAgo(now, 20), null, "Preço acima do orçamento"),
            deal("Tour virtual dos imóveis", fernanda, "5400", DealStage.LOST, DealSource.WEBSITE, daysAgo(now, 40), null, "Sem resposta do cliente")
        );
        dealRepository.saveAll(deals);
        stageChangeRepository.saveAll(deals.stream()
                .map(deal -> new DealStageChange(deal, null, deal.getStage(), deal.getStageChangedAt()))
                .toList());

        log.info("Dados de demonstração criados: {} clientes e {} negócios", 8, deals.size());
    }

    private static Instant daysAgo(Instant now, int days) {
        return now.minus(Duration.ofDays(days));
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
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

    private static Deal deal(String title, Customer customer, String value, DealStage stage, DealSource source,
                             Instant stageChangedAt, LocalDate expectedClose, String lostReason) {
        Deal deal = new Deal();
        deal.setTitle(title);
        deal.setCustomer(customer);
        deal.setValue(new BigDecimal(value));
        deal.setStage(stage);
        deal.setSource(source);
        deal.setStageChangedAt(stageChangedAt);
        deal.setClosedAt(stage.isClosed() ? stageChangedAt : null);
        deal.setExpectedCloseDate(expectedClose);
        deal.setLostReason(lostReason);
        return deal;
    }
}
