CREATE EXTENSION IF NOT EXISTS pg_cron;
DO $setup$BEGIN
  IF EXISTS (SELECT * FROM cron.job WHERE jobname = 'update-query-routers-flags') THEN
    PERFORM cron.alter_job(
      jobid,
      '30 seconds',
      $sql$
        DO $$BEGIN
          PERFORM citus_set_node_property(nodename, nodeport, 'shouldhaveshards', false)
          FROM pg_dist_node
          WHERE shouldhaveshards AND groupid IN (SELECT generate_series(%1$s, %2$s));
        END$$
      $sql$,
      %3$s)
    FROM cron.job WHERE jobname = 'update-query-routers-flags';
  ELSE
    PERFORM cron.schedule_in_database(
      'update-query-routers-flags',
      '30 seconds',
      $sql$
        DO $$BEGIN
          PERFORM citus_set_node_property(nodename, nodeport, 'shouldhaveshards', false)
          FROM pg_dist_node
          WHERE shouldhaveshards AND groupid IN (SELECT generate_series(%1$s, %2$s));
        END$$
      $sql$,
      %3$s);
  END IF;
END$setup$;
