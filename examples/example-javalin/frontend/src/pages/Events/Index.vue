<script setup lang="ts">
import { Link, router } from '@inertiajs/vue3'

defineProps<{
  events: Array<{ id: number; title: string; description: string; date: string }>
  auth: { user: { name: string } }
}>()

function deleteEvent(id: number) {
  if (confirm('Are you sure?')) {
    router.delete(`/events/${id}`)
  }
}
</script>

<template>
  <div style="max-width: 600px; margin: 40px auto; font-family: sans-serif;">
    <h1>Events</h1>
    <p>Logged in as {{ auth.user.name }}</p>

    <Link href="/events/create" style="display: inline-block; margin-bottom: 20px; color: #2563eb;">
      + Create Event
    </Link>

    <ul style="list-style: none; padding: 0;">
      <li v-for="event in events" :key="event.id"
          style="border: 1px solid #e5e7eb; padding: 16px; margin-bottom: 8px; border-radius: 8px;">
        <Link :href="`/events/${event.id}`" style="text-decoration: none; color: inherit;">
          <strong>{{ event.title }}</strong>
          <span style="color: #6b7280; margin-left: 8px;">{{ event.date }}</span>
        </Link>
        <button @click="deleteEvent(event.id)"
                style="float: right; color: #dc2626; background: none; border: none; cursor: pointer;">
          Delete
        </button>
      </li>
    </ul>

    <Link href="/" style="color: #6b7280;">← Home</Link>
  </div>
</template>
