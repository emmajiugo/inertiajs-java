<script setup lang="ts">
import { Link } from '@inertiajs/vue3'

defineProps<{
  event: { id: number; title: string; description: string; date: string }
}>()
</script>

<template>
  <div style="max-width: 600px; margin: 40px auto; font-family: sans-serif;">
    <Link href="/events" style="color: #6b7280;">← Back to Events</Link>

    <h1>{{ event.title }}</h1>
    <p style="color: #6b7280;">{{ event.date }}</p>
    <p>{{ event.description }}</p>
  </div>
</template>
