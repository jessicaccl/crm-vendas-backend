# Estudo de caso de produto: CRM Pro

**Autora:** Jessica Cardoso · **Papel:** Product Owner e desenvolvedora · **Período:** janeiro a outubro de 2026
**Demo:** https://crm-pro-jessica.vercel.app · **Código:** [backend](https://github.com/jessicaccl/crm-vendas-backend) · [frontend](https://github.com/jessicaccl/crm-frontend)

> **Resumo.** Os leads do quiz de diagnóstico da Unic Studio chegavam por e-mail e ficavam espalhados.
> Transformei um CRM que eu tinha começado como estudo, depois da residência Fullstack do Serratec,
> em um produto em uso real. Ele recebe os leads do site automaticamente, organiza o funil de vendas,
> registra o histórico de cada cliente e mostra os resultados. Entreguei em 7 fases, cada uma validada
> antes da próxima. Usei o Claude Code (IA) como par de programação. Definir o problema, priorizar e
> validar cada entrega ficou comigo.

---

## 1. Contexto e problema

A Unic Studio tem um **quiz de diagnóstico** no site. O visitante responde algumas perguntas e recebe
uma recomendação (página profissional, gestão de redes, sistema sob medida...). Cada resposta é um lead.

**Como era:**
- Cada lead chegava como **notificação por e-mail** do Netlify, misturado com o resto da caixa de entrada.
- Não havia um lugar para saber **em que pé estava** cada contato (se respondeu, se recebeu proposta, se fechou).
- Não havia histórico das conversas nem lembretes de retorno, então o follow-up dependia da memória.
- Não dava para responder perguntas simples como "quanto vendemos este mês?" ou "qual canal traz mais clientes?".

**Problema em uma frase:** *um pequeno negócio que gera leads pelo site perde oportunidades porque
não tem onde organizar, acompanhar e medir esses contatos.*

## 2. Para quem

**Usuária principal:** dona ou vendedora de um pequeno negócio de serviços, que faz tudo sozinha
(atende, vende, entrega) e usa o celular o dia todo.

**O que ela precisa fazer (jobs to be done):**
1. Ver todos os leads novos num só lugar, sem digitar nada.
2. Saber o que fazer hoje: quem precisa de retorno e o que está parado.
3. Lembrar o que já conversou com cada cliente.
4. Entender se as vendas estão melhorando e de onde vêm os clientes.

**O que ela não quer:** configurar uma ferramenta complexa ou pagar caro por um CRM grande
para um volume pequeno de leads.

## 3. Ponto de partida

- **Um CRM de estudo:** comecei logo após a residência, em janeiro de 2026. Era um cadastro de clientes
  em Java/Spring Boot e React, sem login, sem testes e com a senha do banco no código.
- **Um quiz no ar:** o formulário do Netlify já existia e funcionava. A solução deveria **se encaixar
  nele**, sem exigir refazer o site.

## 4. Opções avaliadas

Antes de codar, montei mockups de três caminhos:

| Opção | O que resolve | Risco |
|---|---|---|
| **A. Funil Kanban** | Organizar e acompanhar cada negócio | Sem dados, vira só um quadro bonito |
| **B. Painel de resultados** | Medir vendas e conversão | Inútil sem dados reais entrando |
| **C. Ficha do cliente e tarefas** | Histórico e follow-up | Exige que o funil já exista |

**Decisão:** fazer as três, porque juntas formam um CRM completo, mas **em fases e numa ordem que
gera valor desde o início**: primeiro organizar (A), depois alimentar com dados reais (integração),
depois acompanhar (C) e só então medir (B).

## 5. Entregas por fase

| Fase | Entrega | Valor para a usuária |
|---|---|---|
| 1 | Base segura: API padronizada, validação, migrações de banco, Docker | Fundação confiável (antes, a senha estava no código) |
| 2 | Funil Kanban com arrastar e soltar e indicadores | Ver todos os negócios e mover de etapa em segundos |
| 3 | Login, testes automatizados, CI e deploy online | Sistema acessível de qualquer lugar e com demo pública |
| 4 | Leads do quiz entrando sozinhos (webhook do Netlify) | Zero digitação: o lead aparece no funil na hora |
| 5 | Multiempresa | Demo pública e dados reais separados no mesmo sistema |
| + | Importação de planilha CSV | Trazer os leads antigos, que já estavam no Netlify |
| 7 | Ficha do cliente, linha do tempo e tarefas | Lembrar o que foi conversado e quando retornar |
| 6 | Painel de resultados | Receita por mês, conversão, origem dos clientes e previsão |

A **fase 7 veio antes da 6** de propósito: com poucos dias de dados reais, um painel teria pouco a mostrar,
enquanto as tarefas de follow-up já ajudavam no dia a dia.

## 6. Decisões de produto e trocas

**1. Integrar com o que já existe, sem criar outro formulário.**
O quiz já funcionava no Netlify. Em vez de refazer o site, o CRM recebe o mesmo formulário por webhook.
A **troca** é depender do formato do Netlify. Para reduzir esse risco, a entrada de leads virou um
serviço único, reaproveitado pela importação de planilha e pronto para receber outras fontes.

**2. De "dois ambientes" para multiempresa.**
Eu precisava de uma **demo pública**, com dados fictícios para recrutadores testarem, separada dos
**dados reais** de clientes (LGPD). A primeira ideia foi publicar o sistema duas vezes. Quando percebi
que isso significaria um deploy novo **para cada cliente**, mudei a direção: um único sistema em que
cada empresa só enxerga os próprios dados.
A **troca** é que o isolamento precisa ser perfeito. Por isso, ele tem testes automatizados específicos:
uma empresa que tenta acessar um dado de outra recebe "não encontrado".

**3. Não duplicar contatos.**
A mesma pessoa pode responder o quiz duas vezes, e o e-mail é opcional. O sistema reconhece o cliente
pelo e-mail **ou** pelo telefone (comparando só os dígitos) e ignora o reenvio do mesmo lead.

**4. Importação de planilha surgiu do uso real.**
Ao ligar a integração, apareceu a pergunta: *"e os leads antigos?"*. O webhook só traz os novos.
A importação aceita o CSV exportado pelo Netlify, pelo Excel e pelo Google Sheets, e mostra um relatório
do que entrou, do que foi ignorado e por quê. Todo cliente novo de um CRM chega com uma planilha antiga,
então facilitar a migração faz parte do produto.

**5. Motivo da perda obrigatório.**
Ao arrastar um negócio para "Perdido", o sistema pede o motivo. Isso custa um clique a mais, mas é o
que permite ao painel responder *"por que estamos perdendo vendas?"*.

**6. O quadro mostra o que é acionável.**
Negócios ganhos e perdidos aparecem no Kanban só nos últimos 30 dias. O histórico completo fica no painel.
Sem isso, as colunas finais cresceriam sem parar e esconderiam o que importa: o que ainda está em aberto.

**7. Custo zero, com a limitação comunicada.**
Tudo roda em planos gratuitos (Render, Vercel e Neon). O servidor gratuito "dorme" sem uso. Para lidar
com isso, um monitor externo mantém a API acordada, e a tela de login avisa quando o servidor está demorando.

**8. O que eu decidi não fazer.**
Alertas pelo Telegram para cada lead novo foram considerados e **descartados**: a notificação por e-mail
do Netlify já cumpre esse papel, e o valor estava em organizar os leads, não em avisar de novo.

## 7. Exemplo de história de usuário

> **Como** dona de um pequeno negócio,
> **quero** que as respostas do quiz do meu site virem negócios no meu funil automaticamente,
> **para** não perder nenhum lead nem precisar digitar dados.

**Critérios de aceite:**
- [x] Uma resposta enviada no quiz aparece em "Prospecto" sem nenhuma ação manual.
- [x] O negócio mostra o resultado do diagnóstico e a pontuação nas observações, com origem "Site".
- [x] Se a pessoa já é cliente (mesmo e-mail ou telefone), o negócio é ligado ao cadastro existente.
- [x] O mesmo envio recebido duas vezes não gera duplicata.
- [x] Envios sem a assinatura válida do Netlify são recusados.
- [x] O lead entra somente na empresa dona da integração.

## 8. Como medir o sucesso

| Pergunta | Métrica | Resultado |
|---|---|---|
| Os leads estão chegando sozinhos? | % de leads do quiz que entram no CRM sem digitação | 100% desde a ativação (o primeiro lead real chegou em 11/10/2026) |
| Algum lead fica sem retorno? | Leads sem tarefa ou sem mudança de etapa há mais de 7 dias | *a medir após 30 dias de uso* |
| O funil está saudável? | Taxa de conversão de prospecto para ganho | *a medir após 30 dias de uso* |
| De onde vêm os clientes? | Ganhos por origem (site, Instagram, indicação...) | *a medir após 30 dias de uso* |

O painel da fase 6 já calcula essas métricas. O próximo passo é acompanhar os números com o uso real.

## 9. Limitações conhecidas

- **Uma pessoa por empresa:** ainda não há convite de equipe nem permissões por função.
- **O painel calcula tudo em memória:** funciona bem com centenas de negócios, mas para dezenas de
  milhares seria preciso mover os cálculos para o banco.
- **Segurança:** o login guarda o token no navegador e não limita tentativas. Para um uso maior,
  eu adicionaria limite de tentativas e um token de renovação.
- **Validação:** até agora, a única usuária real sou eu. Ainda falta testar com pessoas de fora.

## 10. Próximos passos (backlog priorizado)

1. **Medir 30 dias de uso real** e preencher a seção 8. Sem isso, não sei o que priorizar depois.
2. **Testar com 2 ou 3 pequenos negócios de fora** e observar onde travam.
3. **Convidar a equipe** e criar perfis de acesso, o primeiro pedido provável de uma empresa com mais de uma pessoa.
4. **Atalho para o WhatsApp com mensagem pronta**, a partir do resultado do diagnóstico.
5. **Novas fontes de leads** (Instagram, outros formulários), reaproveitando o mesmo serviço de entrada.

## 11. Aprendizados

- **Comece pelo fluxo de dados reais.** O momento em que o primeiro lead de verdade apareceu no funil
  validou mais do que qualquer tela pronta.
- **Rever uma decisão no meio do caminho é normal.** A troca de "dois ambientes" por multiempresa veio
  de uma pergunta simples: *"e se fossem dez clientes?"*.
- **O uso real revela o que faltou.** A importação de planilha não estava no plano e entrou por ser necessária.
- **A IA acelera a construção, mas não decide.** O Claude Code escreveu boa parte do código, com velocidade.
  Decidir o que construir, em que ordem e o que deixar de fora continuou sendo trabalho de produto.
