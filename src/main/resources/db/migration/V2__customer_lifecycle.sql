-- O status do cliente passa a ser o ciclo de vida (Lead, Cliente, Inativo).
-- As etapas do funil (Prospecto, Qualificado, Proposta...) vão para os Negócios na fase 2.

alter table tb_customers add column if not exists company varchar(255);
alter table tb_customers add column if not exists updated_at timestamp(6);

update tb_customers set email = null where trim(email) = '';
update tb_customers set email = lower(trim(email)) where email is not null;

update tb_customers
   set status = case
       when status in ('CLOSED', 'WON', 'CUSTOMER') then 'CUSTOMER'
       when status = 'INACTIVE' then 'INACTIVE'
       else 'LEAD'
   end;

update tb_customers set created_at = now() where created_at is null;
update tb_customers set updated_at = created_at where updated_at is null;

-- Datas passam a ter fuso horário. As antigas foram gravadas no horário de Brasília.
alter table tb_customers
    alter column created_at type timestamptz using created_at at time zone 'America/Sao_Paulo',
    alter column updated_at type timestamptz using updated_at at time zone 'America/Sao_Paulo';

alter table tb_customers alter column name set not null;
alter table tb_customers alter column status set not null;
alter table tb_customers alter column status set default 'LEAD';
alter table tb_customers
    add constraint ck_customers_status check (status in ('LEAD', 'CUSTOMER', 'INACTIVE'));
