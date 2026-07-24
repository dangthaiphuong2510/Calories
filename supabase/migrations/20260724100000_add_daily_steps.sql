-- daily_steps (offline-first Health Connect sync)
-- Apply in Supabase SQL editor or via CLI: supabase db push

create table if not exists public.daily_steps (
  date date not null,
  user_id uuid not null references auth.users (id) on delete cascade,
  step_count bigint not null default 0,
  calories_burned double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  is_dirty boolean not null default false,
  primary key (date, user_id)
);

create index if not exists idx_daily_steps_user_date
  on public.daily_steps (user_id, date desc);

alter table public.daily_steps enable row level security;

create policy "daily_steps_select_own"
  on public.daily_steps for select
  using (auth.uid() = user_id);

create policy "daily_steps_insert_own"
  on public.daily_steps for insert
  with check (auth.uid() = user_id);

create policy "daily_steps_update_own"
  on public.daily_steps for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "daily_steps_delete_own"
  on public.daily_steps for delete
  using (auth.uid() = user_id);
