create table tenant (id varchar(100) primary key, kind varchar(30) not null, created_at timestamptz not null default now());
create table llm_request (
  id uuid primary key, tenant_id varchar(100) not null references tenant(id), application varchar(50) not null,
  provider varchar(20) not null, model varchar(100) not null, credential_source varchar(20) not null,
  prompt_version varchar(100), input_tokens integer not null default 0, output_tokens integer not null default 0,
  estimated_cost numeric(16,8) not null default 0, latency_ms bigint not null, cache_hit boolean not null default false,
  fallback_used boolean not null default false, status varchar(20) not null, created_at timestamptz not null default now()
);
create index ix_llm_request_tenant_created on llm_request(tenant_id, created_at desc);
create index ix_llm_request_created on llm_request(created_at desc);
